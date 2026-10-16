import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Subject, debounceTime } from 'rxjs';
import { Page } from '../../core/models';
import { QuoteApi } from '../../core/sales.api';
import { QUOTE_STATUS_LABELS, QuoteSummary } from '../../core/sales.models';
import { ToastService } from '../../core/toast.service';
import { PaginationComponent } from '../../shared/pagination.component';
import { MadPipe, PillComponent, entries } from '../../shared/ui';

@Component({
  selector: 'app-quote-list',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe, MadPipe, PillComponent, PaginationComponent],
  template: `
    <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <h1>Devis</h1>
        <p class="text-sm text-slate-500">Propositions commerciales ; un devis accepté devient une commande et génère sa facture.</p>
      </div>
      <div class="flex gap-2">
        <a routerLink="/invoices" class="btn-secondary">Factures</a>
        <a routerLink="/products" class="btn-secondary">Catalogue</a>
      </div>
    </div>

    <div class="card mt-6">
      <div class="grid grid-cols-1 gap-3 border-b border-slate-200 p-4 sm:grid-cols-3">
        <input class="input sm:col-span-2" placeholder="Rechercher (numéro, client)…" [(ngModel)]="q" (ngModelChange)="search$.next()" />
        <select class="input" [(ngModel)]="status" (ngModelChange)="load(0)">
          <option value="">Tous les statuts</option>
          @for (s of statuses; track s[0]) { <option [value]="s[0]">{{ s[1] }}</option> }
        </select>
      </div>
      <div class="overflow-x-auto">
        <table class="table">
          <thead class="bg-slate-50">
            <tr><th>Numéro</th><th>Client</th><th class="hidden md:table-cell">Opportunité</th><th class="hidden sm:table-cell">Date</th><th class="text-right">Total TTC</th><th>Statut</th></tr>
          </thead>
          <tbody>
            @for (q of page()?.content; track q.id) {
              <tr class="cursor-pointer" (click)="router.navigate(['/quotes', q.id])">
                <td class="font-medium text-slate-900">{{ q.number }}</td>
                <td>{{ q.company.label }}</td>
                <td class="hidden md:table-cell">{{ q.opportunity.label }}</td>
                <td class="hidden sm:table-cell">{{ q.date | date: 'dd/MM/yyyy' }}</td>
                <td class="text-right tabular-nums">{{ q.totalTTC | mad }}</td>
                <td><app-pill [value]="q.status" [labels]="labels" /></td>
              </tr>
            } @empty {
              <tr><td colspan="6" class="py-10 text-center text-slate-500">Aucun devis. Créez-en un depuis une opportunité.</td></tr>
            }
          </tbody>
        </table>
      </div>
      @if (page(); as p) {
        <app-pagination [page]="p.page" [size]="p.size" [total]="p.totalElements" [totalPages]="p.totalPages" (pageChange)="load($event)" />
      }
    </div>
  `
})
export class QuoteListComponent implements OnInit {
  private api = inject(QuoteApi);
  private toast = inject(ToastService);
  router = inject(Router);
  labels = QUOTE_STATUS_LABELS;
  statuses = entries(QUOTE_STATUS_LABELS);
  q = '';
  status = '';
  page = signal<Page<QuoteSummary> | null>(null);
  search$ = new Subject<void>();

  ngOnInit(): void {
    this.search$.pipe(debounceTime(300)).subscribe(() => this.load(0));
    this.load(0);
  }

  load(page: number): void {
    this.api.search({ q: this.q, status: this.status, page, size: 20 }).subscribe({
      next: (p) => this.page.set(p),
      error: (err) => this.toast.error(err)
    });
  }
}
