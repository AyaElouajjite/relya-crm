import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Subject, debounceTime } from 'rxjs';
import { UserApi } from '../../core/api.service';
import { Page, UserSummary } from '../../core/models';
import { FileApi, LeadApi } from '../../core/sales.api';
import { LEAD_SOURCE_LABELS, LEAD_STATUS_LABELS, Lead } from '../../core/sales.models';
import { ToastService } from '../../core/toast.service';
import { HasPermissionDirective } from '../../shared/has-permission.directive';
import { ImportDialogComponent } from '../../shared/import-dialog.component';
import { PaginationComponent } from '../../shared/pagination.component';
import { PillComponent, ScoreComponent, entries } from '../../shared/ui';

@Component({
  selector: 'app-lead-list',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe, HasPermissionDirective, PaginationComponent, ImportDialogComponent, PillComponent, ScoreComponent],
  template: `
    <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <h1>Leads</h1>
        <p class="text-sm text-slate-500">Prospects à qualifier, triés par score de conversion estimé par l’IA.</p>
      </div>
      <div class="flex flex-wrap gap-2">
        <button *appCan="'LEAD_ASSIGN'" class="btn-secondary" (click)="rescoreAll()">Recalculer les scores</button>
        <button *appCan="'DATA_IMPORT'" class="btn-secondary" (click)="showImport.set(true)">Importer</button>
        <button *appCan="'DATA_EXPORT'" class="btn-secondary" (click)="exportCsv()">Exporter</button>
        <a routerLink="/leads/new" class="btn-primary">+ Nouveau lead</a>
      </div>
    </div>

    <div class="card mt-6">
      <div class="grid grid-cols-1 gap-3 border-b border-slate-200 p-4 sm:grid-cols-2 lg:grid-cols-5">
        <input class="input lg:col-span-2" placeholder="Rechercher (nom, e-mail, entreprise)…" [(ngModel)]="f.q" (ngModelChange)="search$.next()" />
        <select class="input" [(ngModel)]="f.status" (ngModelChange)="load(0)">
          <option value="">Statuts ouverts</option>
          @for (s of statuses; track s[0]) { <option [value]="s[0]">{{ s[1] }}</option> }
        </select>
        <select class="input" [(ngModel)]="f.source" (ngModelChange)="load(0)">
          <option value="">Toutes les sources</option>
          @for (s of sources; track s[0]) { <option [value]="s[0]">{{ s[1] }}</option> }
        </select>
        <select class="input" [(ngModel)]="f.assignedToId" (ngModelChange)="load(0)">
          <option value="">Tous les commerciaux</option>
          @for (u of owners(); track u.id) { <option [value]="u.id">{{ u.fullName }}</option> }
        </select>
      </div>
      <div class="overflow-x-auto">
        <table class="table">
          <thead class="bg-slate-50">
            <tr>
              <th>Lead</th>
              <th class="hidden md:table-cell">Source</th>
              <th><button (click)="sortBy('score')">Score {{ sort === 'score' ? (dir === 'desc' ? '↓' : '↑') : '' }}</button></th>
              <th>Statut</th>
              <th class="hidden lg:table-cell">Attribué à</th>
              <th class="hidden sm:table-cell"><button (click)="sortBy('createdAt')">Créé le {{ sort === 'createdAt' ? (dir === 'desc' ? '↓' : '↑') : '' }}</button></th>
            </tr>
          </thead>
          <tbody>
            @for (l of page()?.content; track l.id) {
              <tr class="cursor-pointer" (click)="router.navigate(['/leads', l.id])">
                <td>
                  <span class="font-medium text-slate-900">{{ l.fullName }}</span>
                  <span class="block text-xs text-slate-500">{{ l.companyName ?? '' }}{{ l.position ? ' · ' + l.position : '' }}</span>
                </td>
                <td class="hidden md:table-cell">{{ sourceLabels[l.source] }}</td>
                <td><app-score [value]="l.score" /></td>
                <td><app-pill [value]="l.status" [labels]="statusLabels" /></td>
                <td class="hidden lg:table-cell">{{ l.assignedTo?.fullName ?? 'Non attribué' }}</td>
                <td class="hidden sm:table-cell">{{ l.createdAt | date: 'shortDate' }}</td>
              </tr>
            } @empty {
              <tr><td colspan="6" class="py-10 text-center text-slate-500">Aucun lead ne correspond à ces critères.</td></tr>
            }
          </tbody>
        </table>
      </div>
      @if (page(); as p) {
        <app-pagination [page]="p.page" [size]="p.size" [total]="p.totalElements" [totalPages]="p.totalPages" (pageChange)="load($event)" />
      }
    </div>

    @if (showImport()) {
      <app-import-dialog title="Importer des leads" columns="prenom; nom; email; telephone; entreprise; poste; secteur; ville; budget; commercial"
                         [upload]="upload" (close)="showImport.set(false)" (imported)="load(0)" />
    }
  `
})
export class LeadListComponent implements OnInit {
  private api = inject(LeadApi);
  private users = inject(UserApi);
  private files = inject(FileApi);
  private toast = inject(ToastService);
  router = inject(Router);

  statuses = entries(LEAD_STATUS_LABELS);
  sources = entries(LEAD_SOURCE_LABELS);
  statusLabels = LEAD_STATUS_LABELS;
  sourceLabels = LEAD_SOURCE_LABELS;
  f = { q: '', status: '', source: '', assignedToId: '' };
  sort = 'score';
  dir: 'asc' | 'desc' = 'desc';
  page = signal<Page<Lead> | null>(null);
  owners = signal<UserSummary[]>([]);
  showImport = signal(false);
  search$ = new Subject<void>();
  upload = (file: File) => this.api.import(file);

  ngOnInit(): void {
    this.search$.pipe(debounceTime(300)).subscribe(() => this.load(0));
    this.users.assignable().subscribe((u) => this.owners.set(u));
    this.load(0);
  }

  load(page: number): void {
    this.api.search({ ...this.f, page, size: 20, sort: this.sort, dir: this.dir }).subscribe({
      next: (p) => this.page.set(p),
      error: (err) => this.toast.error(err)
    });
  }

  sortBy(field: string): void {
    this.dir = this.sort === field && this.dir === 'desc' ? 'asc' : 'desc';
    this.sort = field;
    this.load(0);
  }

  rescoreAll(): void {
    this.api.rescoreAll().subscribe({
      next: (r) => {
        this.toast.success(`${r.scored} lead(s) re-scoré(s)`);
        this.load(0);
      },
      error: (err) => this.toast.error(err)
    });
  }

  exportCsv(): void {
    this.files.download('/api/leads/export', this.f, 'leads.csv').subscribe({ error: (err) => this.toast.error(err) });
  }
}
