import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Subject, debounceTime } from 'rxjs';
import { CompanyApi, ContactApi, UserApi } from '../../core/api.service';
import { FileApi, PlatformApi } from '../../core/sales.api';
import { Contact, Page, UserSummary } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { HasPermissionDirective } from '../../shared/has-permission.directive';
import { ImportDialogComponent } from '../../shared/import-dialog.component';
import { PaginationComponent } from '../../shared/pagination.component';

@Component({
  selector: 'app-contact-list',
  standalone: true,
  imports: [FormsModule, RouterLink, HasPermissionDirective, PaginationComponent, ImportDialogComponent],
  template: `
    <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <h1>Contacts</h1>
        <p class="text-sm text-slate-500">Interlocuteurs chez vos clients et prospects.</p>
      </div>
      <div class="flex flex-wrap gap-2">
        <button *appCan="'DATA_IMPORT'" class="btn-secondary" (click)="showImport.set(true)">Importer</button>
        <button *appCan="'DATA_EXPORT'" class="btn-secondary" (click)="exportCsv()">CSV</button>
        <button *appCan="'DATA_EXPORT'" class="btn-secondary" (click)="exportCsv('xlsx')">Excel</button>
        <a *appCan="'CONTACT_WRITE'" routerLink="/contacts/new" class="btn-primary">+ Nouveau contact</a>
      </div>
    </div>

    <div class="card mt-6">
      <div class="grid grid-cols-1 gap-3 border-b border-slate-200 p-4 sm:grid-cols-3 lg:grid-cols-6">
        <input class="input lg:col-span-2" placeholder="Rechercher (nom, email, téléphone, entreprise)…"
               [(ngModel)]="filters.q" (ngModelChange)="search$.next()" />
        <select class="input" [(ngModel)]="filters.sector" (ngModelChange)="reload()">
          <option value="">Tous secteurs</option>
          @for (s of sectors(); track s) { <option [value]="s">{{ s }}</option> }
        </select>
        <select class="input" [(ngModel)]="filters.city" (ngModelChange)="reload()">
          <option value="">Toutes villes</option>
          @for (c of cities(); track c) { <option [value]="c">{{ c }}</option> }
        </select>
        <select class="input" [(ngModel)]="filters.companyStatus" (ngModelChange)="reload()">
          <option value="">Clients et prospects</option>
          <option value="CLIENT">Clients</option>
          <option value="PROSPECT">Prospects</option>
          <option value="INACTIF">Inactifs</option>
        </select>
        <select class="input" [(ngModel)]="filters.ownerId" (ngModelChange)="reload()">
          <option value="">Tous les commerciaux</option>
          @for (u of owners(); track u.id) { <option [value]="u.id">{{ u.fullName }}</option> }
        </select>
        <div class="flex items-center gap-4 text-sm">
          @if (filters.tag) {
            <span class="badge gap-1 bg-brand-50 text-brand-700">
              tag : {{ filters.tag }} <button (click)="filters.tag = ''; reload()" aria-label="Retirer le filtre">×</button>
            </span>
          }
          <label class="flex items-center gap-2 text-slate-600">
            <input type="checkbox" class="rounded border-slate-300" [(ngModel)]="filters.archived" (ngModelChange)="reload()" />
            Archivés
          </label>
        </div>
      </div>

      <div class="overflow-x-auto">
        <table class="table">
          <thead class="bg-slate-50">
            <tr>
              <th>Nom</th>
              <th class="hidden sm:table-cell">Entreprise</th>
              <th class="hidden md:table-cell">Email</th>
              <th class="hidden lg:table-cell">Téléphone</th>
              <th class="hidden lg:table-cell">Tags</th>
              <th class="text-right"></th>
            </tr>
          </thead>
          <tbody>
            @for (c of page()?.content; track c.id) {
              <tr>
                <td>
                  <span class="font-medium text-slate-900">{{ c.fullName }}</span>
                  @if (c.position) { <span class="block text-xs text-slate-500">{{ c.position }}</span> }
                </td>
                <td class="hidden sm:table-cell">
                  @if (c.companyId) {
                    <a [routerLink]="['/companies', c.companyId]" class="text-brand-600 hover:underline">{{ c.companyName }}</a>
                  } @else { — }
                </td>
                <td class="hidden md:table-cell">
                  @if (c.email) { <a [href]="'mailto:' + c.email" class="hover:underline">{{ c.email }}</a> } @else { — }
                </td>
                <td class="hidden lg:table-cell">{{ c.phone ?? '—' }}</td>
                <td class="hidden lg:table-cell">
                  <div class="flex flex-wrap gap-1">
                    @for (t of c.tags; track t) {
                      <button class="badge bg-slate-100 text-slate-600 hover:bg-brand-50" (click)="filters.tag = t; reload()">{{ t }}</button>
                    }
                  </div>
                </td>
                <td class="whitespace-nowrap text-right">
                  <ng-container *appCan="'CONTACT_WRITE'">
                    <a [routerLink]="['/contacts', c.id, 'edit']" class="btn-ghost">Modifier</a>
                    <button class="btn-ghost" (click)="toggleArchive(c)">{{ c.archived ? 'Restaurer' : 'Archiver' }}</button>
                  </ng-container>
                  <button *appCan="'CONTACT_DELETE'" class="btn-ghost text-red-600" (click)="remove(c)">Supprimer</button>
                  @if (c.companyId && c.email && !c.archived) {
                    <button *appCan="['TICKET_MANAGE', 'USER_MANAGE']" class="btn-ghost" (click)="invite(c)" title="Créer un accès à l'espace client">Portail</button>
                  }
                  <ng-container *appCan="'DATA_PRIVACY'">
                    <button class="btn-ghost" (click)="exportData(c)" title="Droit d'accès (loi 09-08)">Données</button>
                    @if (!c.anonymized) { <button class="btn-ghost text-red-600" (click)="anonymize(c)" title="Droit d'effacement (loi 09-08)">Anonymiser</button> }
                  </ng-container>
                </td>
              </tr>
            } @empty {
              <tr><td colspan="6" class="py-10 text-center text-slate-500">
                {{ loading() ? 'Chargement…' : 'Aucun contact ne correspond à ces critères.' }}
              </td></tr>
            }
          </tbody>
        </table>
      </div>
      @if (page(); as p) {
        <app-pagination [page]="p.page" [size]="p.size" [total]="p.totalElements" [totalPages]="p.totalPages"
                        (pageChange)="load($event)" />
      }
    </div>

    @if (showImport()) {
      <app-import-dialog title="Importer des contacts" columns="prenom; nom; email; telephone; poste; entreprise; tags"
                         [upload]="upload" (close)="showImport.set(false)" (imported)="reload()" />
    }
  `
})
export class ContactListComponent implements OnInit {
  private api = inject(ContactApi);
  private users = inject(UserApi);
  private toast = inject(ToastService);
  router = inject(Router);

  filters = { q: '', tag: '', ownerId: '', archived: false, sector: '', city: '', companyStatus: '' };
  sectors = signal<string[]>([]);
  cities = signal<string[]>([]);
  private companies = inject(CompanyApi);
  private platform = inject(PlatformApi);
  private files = inject(FileApi);
  page = signal<Page<Contact> | null>(null);
  loading = signal(false);
  owners = signal<UserSummary[]>([]);
  showImport = signal(false);
  search$ = new Subject<void>();
  upload = (file: File) => this.api.import(file);

  ngOnInit(): void {
    this.search$.pipe(debounceTime(300)).subscribe(() => this.reload());
    this.users.assignable().subscribe((u) => this.owners.set(u));
    this.companies.filters().subscribe((f) => {
      this.sectors.set(f.sectors);
      this.cities.set(f.cities);
    });
    this.reload();
  }

  reload(): void {
    this.load(0);
  }

  load(pageIndex: number): void {
    this.loading.set(true);
    this.api.search({ ...this.filters, page: pageIndex, size: 20 }).subscribe({
      next: (p) => {
        this.page.set(p);
        this.loading.set(false);
      },
      error: (err) => {
        this.toast.error(err);
        this.loading.set(false);
      }
    });
  }

  toggleArchive(c: Contact): void {
    this.api.setArchived(c.id, !c.archived).subscribe({
      next: () => {
        this.toast.success(c.archived ? 'Contact restauré' : 'Contact archivé');
        this.load(this.page()?.page ?? 0);
      },
      error: (err) => this.toast.error(err)
    });
  }

  remove(c: Contact): void {
    if (!confirm(`Supprimer définitivement ${c.fullName} ?`)) return;
    this.api.delete(c.id).subscribe({
      next: () => {
        this.toast.success('Contact supprimé');
        this.load(this.page()?.page ?? 0);
      },
      error: (err) => this.toast.error(err)
    });
  }

  exportCsv(format: 'csv' | 'xlsx' = 'csv'): void {
    this.files.download('/api/contacts/export', { ...this.filters, format }, `contacts.${format}`)
      .subscribe({ error: (err) => this.toast.error(err, "L'export a échoué") });
  }

  invite(c: Contact): void {
    if (!confirm(`Ouvrir un accès à l’espace client pour ${c.fullName} (${c.email}) ? Un e-mail d’activation lui sera envoyé.`)) return;
    this.platform.invitePortal(c.id).subscribe({
      next: () => this.toast.success('Invitation au portail envoyée'),
      error: (err) => this.toast.error(err)
    });
  }

  exportData(c: Contact): void {
    this.platform.exportPersonalData(c.id).subscribe({
      next: (data) => {
        const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = `donnees-${c.lastName.toLowerCase()}.json`;
        a.click();
        URL.revokeObjectURL(a.href);
      },
      error: (err) => this.toast.error(err)
    });
  }

  anonymize(c: Contact): void {
    if (!confirm(`Anonymiser définitivement ${c.fullName} ? Ses données personnelles seront effacées (l’historique commercial est conservé).`)) return;
    this.platform.anonymizeContact(c.id).subscribe({
      next: () => {
        this.toast.success('Contact anonymisé');
        this.load(this.page()?.page ?? 0);
      },
      error: (err) => this.toast.error(err)
    });
  }
}
