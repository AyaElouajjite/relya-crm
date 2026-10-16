import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, debounceTime } from 'rxjs';
import { CompanyApi, UserApi } from '../../core/api.service';
import { FileApi } from '../../core/sales.api';
import { COMPANY_STATUS_LABELS, Company, CompanyStatus, Page, UserSummary } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { HasPermissionDirective } from '../../shared/has-permission.directive';
import { ImportDialogComponent } from '../../shared/import-dialog.component';
import { PaginationComponent } from '../../shared/pagination.component';
import { StatusBadgeComponent } from '../../shared/status-badge.component';

interface CompanyFilters {
  q: string;
  sector: string;
  city: string;
  status: CompanyStatus | '';
  tag: string;
  ownerId: string;
  archived: boolean;
}

@Component({
  selector: 'app-company-list',
  standalone: true,
  imports: [FormsModule, RouterLink, HasPermissionDirective, PaginationComponent, StatusBadgeComponent, ImportDialogComponent],
  template: `
    <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <h1>Entreprises</h1>
        <p class="text-sm text-slate-500">Clients et prospects de l’entreprise.</p>
      </div>
      <div class="flex flex-wrap gap-2">
        <button *appCan="'DATA_IMPORT'" class="btn-secondary" (click)="showImport.set(true)">Importer</button>
        <button *appCan="'DATA_EXPORT'" class="btn-secondary" (click)="exportCsv()">CSV</button>
        <button *appCan="'DATA_EXPORT'" class="btn-secondary" (click)="exportCsv('xlsx')">Excel</button>
        <a *appCan="'CONTACT_WRITE'" routerLink="/companies/new" class="btn-primary">+ Nouvelle entreprise</a>
      </div>
    </div>

    <div class="card mt-6">
      <div class="grid grid-cols-1 gap-3 border-b border-slate-200 p-4 sm:grid-cols-2 lg:grid-cols-6">
        <input class="input lg:col-span-2" placeholder="Rechercher (nom, ICE, ville, email)…"
               [(ngModel)]="filters.q" (ngModelChange)="search$.next()" />
        <select class="input" [(ngModel)]="filters.status" (ngModelChange)="reload()">
          <option value="">Tous les statuts</option>
          @for (s of statuses; track s[0]) { <option [value]="s[0]">{{ s[1] }}</option> }
        </select>
        <select class="input" [(ngModel)]="filters.sector" (ngModelChange)="reload()">
          <option value="">Tous les secteurs</option>
          @for (s of sectors(); track s) { <option [value]="s">{{ s }}</option> }
        </select>
        <select class="input" [(ngModel)]="filters.city" (ngModelChange)="reload()">
          <option value="">Toutes les villes</option>
          @for (c of cities(); track c) { <option [value]="c">{{ c }}</option> }
        </select>
        <select class="input" [(ngModel)]="filters.ownerId" (ngModelChange)="reload()">
          <option value="">Tous les commerciaux</option>
          @for (u of owners(); track u.id) { <option [value]="u.id">{{ u.fullName }}</option> }
        </select>
        <div class="flex items-center gap-4 text-sm lg:col-span-6">
          @if (filters.tag) {
            <span class="badge gap-1 bg-brand-50 text-brand-700">
              tag : {{ filters.tag }}
              <button (click)="filters.tag = ''; reload()" aria-label="Retirer le filtre">×</button>
            </span>
          }
          <label class="flex items-center gap-2 text-slate-600">
            <input type="checkbox" class="rounded border-slate-300" [(ngModel)]="filters.archived" (ngModelChange)="reload()" />
            Afficher les archivées
          </label>
        </div>
      </div>

      <div class="overflow-x-auto">
        <table class="table">
          <thead class="bg-slate-50">
            <tr>
              <th><button (click)="sortBy('name')">Raison sociale {{ arrow('name') }}</button></th>
              <th class="hidden md:table-cell"><button (click)="sortBy('sector')">Secteur {{ arrow('sector') }}</button></th>
              <th class="hidden sm:table-cell"><button (click)="sortBy('city')">Ville {{ arrow('city') }}</button></th>
              <th><button (click)="sortBy('status')">Statut {{ arrow('status') }}</button></th>
              <th class="hidden lg:table-cell">Tags</th>
              <th class="hidden lg:table-cell">Commercial</th>
              <th class="hidden md:table-cell text-right">Contacts</th>
            </tr>
          </thead>
          <tbody>
            @for (c of page()?.content; track c.id) {
              <tr class="cursor-pointer" (click)="open(c)">
                <td>
                  <span class="font-medium text-slate-900">{{ c.name }}</span>
                  @if (c.ice) { <span class="block text-xs text-slate-400">ICE {{ c.ice }}</span> }
                </td>
                <td class="hidden md:table-cell">{{ c.sector ?? '—' }}</td>
                <td class="hidden sm:table-cell">{{ c.city ?? '—' }}</td>
                <td><app-status-badge [status]="c.status" /></td>
                <td class="hidden lg:table-cell">
                  <div class="flex flex-wrap gap-1">
                    @for (t of c.tags; track t) {
                      <button class="badge bg-slate-100 text-slate-600 hover:bg-brand-50" (click)="filterTag(t, $event)">{{ t }}</button>
                    }
                  </div>
                </td>
                <td class="hidden lg:table-cell">{{ c.owner?.fullName ?? '—' }}</td>
                <td class="hidden md:table-cell text-right">{{ c.contactCount }}</td>
              </tr>
            } @empty {
              <tr><td colspan="7" class="py-10 text-center text-slate-500">
                {{ loading() ? 'Chargement…' : 'Aucune entreprise ne correspond à ces critères.' }}
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
      <app-import-dialog title="Importer des entreprises"
                         columns="raisonSociale; secteur; ice; ville; adresse; telephone; email; siteWeb; statut; tags; commercial"
                         [upload]="upload" (close)="showImport.set(false)" (imported)="reload(); loadFilters()" />
    }
  `
})
export class CompanyListComponent implements OnInit {
  private api = inject(CompanyApi);
  private users = inject(UserApi);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private toast = inject(ToastService);
  private files = inject(FileApi);

  statuses = Object.entries(COMPANY_STATUS_LABELS) as [CompanyStatus, string][];
  filters: CompanyFilters = { q: '', sector: '', city: '', status: '', tag: '', ownerId: '', archived: false };
  sort = 'name';
  dir: 'asc' | 'desc' = 'asc';

  page = signal<Page<Company> | null>(null);
  loading = signal(false);
  sectors = signal<string[]>([]);
  cities = signal<string[]>([]);
  owners = signal<UserSummary[]>([]);
  showImport = signal(false);
  search$ = new Subject<void>();
  upload = (file: File) => this.api.import(file);

  ngOnInit(): void {
    const status = this.route.snapshot.queryParamMap.get('status');
    if (status) this.filters.status = status as CompanyStatus;
    this.search$.pipe(debounceTime(300)).subscribe(() => this.reload());
    this.loadFilters();
    this.users.assignable().subscribe((u) => this.owners.set(u));
    this.reload();
  }

  loadFilters(): void {
    this.api.filters().subscribe((f) => {
      this.sectors.set(f.sectors);
      this.cities.set(f.cities);
    });
  }

  reload(): void {
    this.load(0);
  }

  load(pageIndex: number): void {
    this.loading.set(true);
    this.api.search({ ...this.filters, page: pageIndex, size: 20, sort: this.sort, dir: this.dir }).subscribe({
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

  sortBy(field: string): void {
    this.dir = this.sort === field && this.dir === 'asc' ? 'desc' : 'asc';
    this.sort = field;
    this.reload();
  }

  arrow(field: string): string {
    return this.sort === field ? (this.dir === 'asc' ? '↑' : '↓') : '';
  }

  filterTag(tag: string, event: Event): void {
    event.stopPropagation();
    this.filters.tag = tag;
    this.reload();
  }

  open(c: Company): void {
    this.router.navigate(['/companies', c.id]);
  }

  exportCsv(format: 'csv' | 'xlsx' = 'csv'): void {
    this.files.download('/api/companies/export', { ...this.filters, format }, `entreprises.${format}`)
      .subscribe({ error: (err) => this.toast.error(err, "L'export a échoué") });
  }
}
