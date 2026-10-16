import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Page } from '../../core/models';
import { FileApi, QuoteApi } from '../../core/sales.api';
import { INVOICE_STATUS_LABELS, Invoice } from '../../core/sales.models';
import { ToastService } from '../../core/toast.service';
import { HasPermissionDirective } from '../../shared/has-permission.directive';
import { PaginationComponent } from '../../shared/pagination.component';
import { MadPipe, PillComponent, entries } from '../../shared/ui';

@Component({
  selector: 'app-invoice-list',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe, MadPipe, PillComponent, PaginationComponent, HasPermissionDirective],
  template: `
    <a routerLink="/quotes" class="text-sm text-brand-600 hover:underline">← Devis</a>
    <h1 class="mt-2">Factures</h1>
    <p class="text-sm text-slate-500">Émises automatiquement à l’acceptation d’un devis. La comptabilité reste hors périmètre.</p>
    <div class="card mt-6">
      <div class="grid grid-cols-1 gap-3 border-b border-slate-200 p-4 sm:grid-cols-3">
        <input class="input sm:col-span-2" placeholder="Rechercher (numéro, client)…" [(ngModel)]="q" (ngModelChange)="load(0)" />
        <select class="input" [(ngModel)]="status" (ngModelChange)="load(0)">
          <option value="">Tous les statuts</option>
          @for (s of statuses; track s[0]) { <option [value]="s[0]">{{ s[1] }}</option> }
        </select>
      </div>
      <div class="overflow-x-auto">
        <table class="table">
          <thead class="bg-slate-50"><tr><th>Numéro</th><th>Client</th><th class="hidden sm:table-cell">Émise le</th><th class="hidden md:table-cell">Échéance</th><th class="text-right">Total TTC</th><th>Statut</th><th></th></tr></thead>
          <tbody>
            @for (i of page()?.content; track i.id) {
              <tr>
                <td class="font-medium text-slate-900">{{ i.number }}<span class="block text-xs text-slate-400">devis {{ i.quote.label }}</span></td>
                <td><a [routerLink]="['/companies', i.company.id]" class="hover:underline">{{ i.company.label }}</a></td>
                <td class="hidden sm:table-cell">{{ i.issueDate | date: 'dd/MM/yyyy' }}</td>
                <td class="hidden md:table-cell" [class.text-red-600]="i.overdue">{{ i.dueDate | date: 'dd/MM/yyyy' }}{{ i.overdue ? ' · en retard' : '' }}</td>
                <td class="text-right tabular-nums">{{ i.totalTTC | mad }}</td>
                <td><app-pill [value]="i.status" [labels]="labels" /></td>
                <td class="whitespace-nowrap text-right">
                  <button class="btn-ghost" (click)="pdf(i)">PDF</button>
                  @if (i.status === 'EMISE') { <button *appCan="'QUOTE_MANAGE'" class="btn-ghost text-emerald-700" (click)="setStatus(i, 'PAYEE')">Marquer payée</button> }
                </td>
              </tr>
            } @empty { <tr><td colspan="7" class="py-10 text-center text-slate-500">Aucune facture.</td></tr> }
          </tbody>
        </table>
      </div>
      @if (page(); as p) { <app-pagination [page]="p.page" [size]="p.size" [total]="p.totalElements" [totalPages]="p.totalPages" (pageChange)="load($event)" /> }
    </div>
  `
})
export class InvoiceListComponent implements OnInit {
  private api = inject(QuoteApi);
  private files = inject(FileApi);
  private toast = inject(ToastService);
  labels = INVOICE_STATUS_LABELS;
  statuses = entries(INVOICE_STATUS_LABELS);
  q = '';
  status = '';
  page = signal<Page<Invoice> | null>(null);

  ngOnInit(): void {
    this.load(0);
  }

  load(page: number): void {
    this.api.invoices({ q: this.q, status: this.status, page, size: 20 }).subscribe({ next: (p) => this.page.set(p), error: (err) => this.toast.error(err) });
  }

  pdf(i: Invoice): void {
    this.files.openPdf(`/api/invoices/${i.id}/pdf`).subscribe({ error: (err) => this.toast.error(err) });
  }

  setStatus(i: Invoice, value: string): void {
    this.api.setInvoiceStatus(i.id, value).subscribe({ next: () => this.load(this.page()?.page ?? 0), error: (err) => this.toast.error(err) });
  }
}
