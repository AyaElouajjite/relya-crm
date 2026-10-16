import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CompanyApi } from '../../core/api.service';
import { Company360 } from '../../core/models';
import {
  ACTIVITY_STATUS_LABELS,
  ACTIVITY_TYPE_LABELS,
  INVOICE_STATUS_LABELS,
  QUOTE_STATUS_LABELS,
  STAGE_LABELS,
  TICKET_STATUS_LABELS
} from '../../core/sales.models';
import { ToastService } from '../../core/toast.service';
import { ActivityDialogComponent } from '../../shared/activity-dialog.component';
import { HasPermissionDirective } from '../../shared/has-permission.directive';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { MadPipe, PillComponent } from '../../shared/ui';

type Tab = 'contacts' | 'opportunities' | 'quotes' | 'history' | 'tickets';

/** Fiche client à 360° : coordonnées, contacts, opportunités, devis et factures, historique, tickets. */
@Component({
  selector: 'app-company-detail',
  standalone: true,
  imports: [RouterLink, DatePipe, HasPermissionDirective, StatusBadgeComponent, MadPipe, PillComponent, ActivityDialogComponent],
  template: `
    <a routerLink="/companies" class="text-sm text-brand-600 hover:underline">← Entreprises</a>

    @if (data(); as d) {
      <div class="mt-2 flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <div class="flex flex-wrap items-center gap-3">
            <h1>{{ d.company.name }}</h1>
            <app-status-badge [status]="d.company.status" />
            @if (d.company.archived) { <span class="badge bg-slate-200 text-slate-700">Archivée</span> }
          </div>
          <p class="mt-1 text-sm text-slate-500">{{ d.company.sector ?? 'Secteur non renseigné' }} · {{ d.company.city ?? 'Ville non renseignée' }}</p>
        </div>
        <div class="flex flex-wrap gap-2">
          <button *appCan="'ACTIVITY_MANAGE'" class="btn-secondary" (click)="showActivity.set(true)">Planifier</button>
          <a *appCan="'OPPORTUNITY_MANAGE'" routerLink="/opportunities/new" [queryParams]="{ companyId: d.company.id }" class="btn-secondary">+ Opportunité</a>
          <ng-container *appCan="'CONTACT_WRITE'">
            <a [routerLink]="['/companies', d.company.id, 'edit']" class="btn-secondary">Modifier</a>
            <button class="btn-secondary" (click)="toggleArchive()">{{ d.company.archived ? 'Restaurer' : 'Archiver' }}</button>
          </ng-container>
          <button *appCan="'CONTACT_DELETE'" class="btn-danger" (click)="remove()">Supprimer</button>
        </div>
      </div>

      <div class="mt-6 grid grid-cols-2 gap-4 lg:grid-cols-4">
        <div class="card p-4"><p class="text-xs text-slate-500">CA facturé (HT)</p><p class="mt-1 text-xl font-semibold">{{ d.revenue | mad: true }}</p></div>
        <div class="card p-4"><p class="text-xs text-slate-500">Pipeline ouvert</p><p class="mt-1 text-xl font-semibold">{{ d.openPipeline | mad: true }}</p></div>
        <div class="card p-4"><p class="text-xs text-slate-500">Contacts</p><p class="mt-1 text-xl font-semibold">{{ d.contacts.length }}</p></div>
        <div class="card p-4"><p class="text-xs text-slate-500">Tickets ouverts</p><p class="mt-1 text-xl font-semibold">{{ openTickets(d) }}</p></div>
      </div>

      <div class="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-3">
        <section class="card p-6">
          <h2>Coordonnées</h2>
          <dl class="mt-4 space-y-3 text-sm">
            <div><dt class="text-slate-500">ICE</dt><dd class="font-medium">{{ d.company.ice ?? '—' }}</dd></div>
            <div><dt class="text-slate-500">Adresse</dt><dd>{{ d.company.address ?? '—' }}</dd></div>
            <div><dt class="text-slate-500">Téléphone</dt>
              <dd>@if (d.company.phone) { <a class="text-brand-600 hover:underline" [href]="'tel:' + d.company.phone">{{ d.company.phone }}</a> } @else { — }</dd></div>
            <div><dt class="text-slate-500">E-mail</dt>
              <dd>@if (d.company.email) { <a class="text-brand-600 hover:underline" [href]="'mailto:' + d.company.email">{{ d.company.email }}</a> } @else { — }</dd></div>
            <div><dt class="text-slate-500">Site web</dt>
              <dd>@if (safeWebsite(d.company.website); as url) { <a class="text-brand-600 hover:underline" [href]="url" target="_blank" rel="noopener">{{ d.company.website }}</a> } @else { — }</dd></div>
            <div><dt class="text-slate-500">Commercial</dt><dd>{{ d.company.owner?.fullName ?? '—' }}</dd></div>
            <div><dt class="text-slate-500">Tags</dt>
              <dd class="mt-1 flex flex-wrap gap-1">@for (t of d.company.tags; track t) { <span class="badge bg-brand-50 text-brand-700">{{ t }}</span> } @empty { — }</dd></div>
            <div><dt class="text-slate-500">Client depuis</dt><dd>{{ d.company.createdAt | date: 'longDate' }}</dd></div>
          </dl>
        </section>

        <section class="card lg:col-span-2">
          <div class="flex gap-1 overflow-x-auto border-b border-slate-200 px-4">
            @for (t of tabs; track t.id) {
              <button class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium"
                      [class]="tab() === t.id ? 'border-brand-600 text-brand-700' : 'border-transparent text-slate-500 hover:text-slate-700'"
                      (click)="tab.set(t.id)">{{ t.label }} ({{ count(d, t.id) }})</button>
            }
          </div>

          @switch (tab()) {
            @case ('contacts') {
              <div class="flex justify-end px-4 pt-4">
                <a *appCan="'CONTACT_WRITE'" routerLink="/contacts/new" [queryParams]="{ companyId: d.company.id }" class="btn-secondary">+ Ajouter un contact</a>
              </div>
              <ul class="divide-y divide-slate-100">
                @for (c of d.contacts; track c.id) {
                  <li class="flex flex-col gap-1 px-6 py-3 sm:flex-row sm:items-center sm:justify-between">
                    <div><p class="font-medium text-slate-900">{{ c.fullName }}</p><p class="text-sm text-slate-500">{{ c.position ?? '' }}</p></div>
                    <div class="flex flex-wrap items-center gap-4 text-sm">
                      @if (c.email) { <a class="text-brand-600 hover:underline" [href]="'mailto:' + c.email">{{ c.email }}</a> }
                      @if (c.phone) { <a class="text-slate-600" [href]="'tel:' + c.phone">{{ c.phone }}</a> }
                      <a *appCan="'CONTACT_WRITE'" [routerLink]="['/contacts', c.id, 'edit']" class="btn-ghost">Modifier</a>
                    </div>
                  </li>
                } @empty { <li class="px-6 py-8 text-center text-sm text-slate-500">Aucun contact.</li> }
              </ul>
            }
            @case ('opportunities') {
              <ul class="divide-y divide-slate-100">
                @for (o of d.opportunities; track o.id) {
                  <li><a [routerLink]="['/opportunities', o.id]" class="flex items-center justify-between gap-3 px-6 py-3 text-sm hover:bg-slate-50">
                    <span><span class="font-medium text-slate-900">{{ o.title }}</span><span class="block text-xs text-slate-500">{{ o.owner?.fullName }} · clôture {{ (o.closedAt ?? o.expectedCloseDate) | date: 'dd/MM/yyyy' }}</span></span>
                    <span class="flex items-center gap-3 whitespace-nowrap">{{ o.amount | mad: true }} <span class="badge bg-slate-100 text-slate-700">{{ stageLabels[o.stage] }}</span></span>
                  </a></li>
                } @empty { <li class="px-6 py-8 text-center text-sm text-slate-500">Aucune opportunité.</li> }
              </ul>
            }
            @case ('quotes') {
              <ul class="divide-y divide-slate-100">
                @for (q of d.quotes; track q.id) {
                  <li><a [routerLink]="['/quotes', q.id]" class="flex items-center justify-between gap-3 px-6 py-3 text-sm hover:bg-slate-50">
                    <span><span class="font-medium">Devis {{ q.number }}</span> <span class="text-slate-500">· {{ q.date | date: 'dd/MM/yyyy' }}</span></span>
                    <span class="flex items-center gap-3">{{ q.totalTTC | mad }} <app-pill [value]="q.status" [labels]="quoteLabels" /></span>
                  </a></li>
                }
                @for (i of d.invoices; track i.id) {
                  <li class="flex items-center justify-between gap-3 px-6 py-3 text-sm">
                    <span><span class="font-medium">Facture {{ i.number }}</span> <span class="text-slate-500">· {{ i.issueDate | date: 'dd/MM/yyyy' }}</span></span>
                    <span class="flex items-center gap-3">{{ i.totalTTC | mad }} <app-pill [value]="i.status" [labels]="invoiceLabels" /></span>
                  </li>
                }
                @if (!d.quotes.length && !d.invoices.length) { <li class="px-6 py-8 text-center text-sm text-slate-500">Aucun devis ni facture.</li> }
              </ul>
            }
            @case ('history') {
              <ul class="max-h-[32rem] divide-y divide-slate-100 overflow-y-auto">
                @for (a of d.history; track a.id) {
                  <li class="flex items-start gap-3 px-6 py-3 text-sm">
                    <span class="badge" [class]="a.type === 'SYSTEME' ? 'bg-slate-100 text-slate-500' : 'bg-brand-50 text-brand-700'">{{ typeLabels[a.type] }}</span>
                    <div class="min-w-0 flex-1">
                      <p class="font-medium text-slate-900">{{ a.subject }}</p>
                      @if (a.description) { <p class="text-slate-500">{{ a.description }}</p> }
                      <p class="text-xs text-slate-400">{{ a.owner?.fullName ?? 'Système' }}@if (a.type !== 'SYSTEME') { · {{ activityStatus[a.status] }} }</p>
                    </div>
                    <span class="whitespace-nowrap text-xs text-slate-400">{{ a.dueAt | date: 'dd/MM/yy HH:mm' }}</span>
                  </li>
                } @empty { <li class="px-6 py-8 text-center text-sm text-slate-500">Aucune interaction enregistrée.</li> }
              </ul>
            }
            @case ('tickets') {
              <ul class="divide-y divide-slate-100">
                @for (t of d.tickets; track t.id) {
                  <li><a [routerLink]="['/tickets', t.id]" class="flex items-center justify-between gap-3 px-6 py-3 text-sm hover:bg-slate-50">
                    <span><span class="font-medium text-slate-900">{{ t.subject }}</span><span class="block text-xs text-slate-500">{{ t.number }} · {{ t.openedAt | date: 'dd/MM/yyyy' }}</span></span>
                    <app-pill [value]="t.status" [labels]="ticketLabels" />
                  </a></li>
                } @empty { <li class="px-6 py-8 text-center text-sm text-slate-500">Aucun ticket.</li> }
              </ul>
            }
          }
        </section>
      </div>

      @if (showActivity()) {
        <app-activity-dialog [links]="{ companyId: d.company.id, label: d.company.name }" (closed)="showActivity.set(false)" (saved)="showActivity.set(false); load()" />
      }
    } @else {
      <p class="mt-8 text-slate-500">Chargement…</p>
    }
  `
})
export class CompanyDetailComponent implements OnInit {
  private api = inject(CompanyApi);
  private router = inject(Router);
  private toast = inject(ToastService);

  id = input.required<string>();
  data = signal<Company360 | null>(null);
  tab = signal<Tab>('contacts');
  showActivity = signal(false);
  stageLabels = STAGE_LABELS;
  quoteLabels = QUOTE_STATUS_LABELS;
  invoiceLabels = INVOICE_STATUS_LABELS;
  ticketLabels = TICKET_STATUS_LABELS;
  typeLabels = ACTIVITY_TYPE_LABELS;
  activityStatus = ACTIVITY_STATUS_LABELS;
  tabs: { id: Tab; label: string }[] = [
    { id: 'contacts', label: 'Contacts' },
    { id: 'opportunities', label: 'Opportunités' },
    { id: 'quotes', label: 'Devis et factures' },
    { id: 'history', label: 'Historique' },
    { id: 'tickets', label: 'Tickets' }
  ];

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.get(+this.id()).subscribe({
      next: (d) => this.data.set(d),
      error: (err) => {
        this.toast.error(err);
        this.router.navigate(['/companies']);
      }
    });
  }

  count(d: Company360, tab: Tab): number {
    return { contacts: d.contacts.length, opportunities: d.opportunities.length, quotes: d.quotes.length + d.invoices.length,
      history: d.history.length, tickets: d.tickets.length }[tab];
  }

  openTickets(d: Company360): number {
    return d.tickets.filter((t) => t.status !== 'RESOLU' && t.status !== 'FERME').length;
  }

  /** N'autorise que http(s) pour éviter les liens javascript: saisis par un utilisateur. */
  safeWebsite(url: string | null): string | null {
    if (!url) return null;
    const full = /^https?:\/\//i.test(url) ? url : `https://${url}`;
    try {
      const parsed = new URL(full);
      return parsed.protocol === 'http:' || parsed.protocol === 'https:' ? parsed.href : null;
    } catch {
      return null;
    }
  }

  toggleArchive(): void {
    const c = this.data()!.company;
    this.api.setArchived(c.id, !c.archived).subscribe({
      next: () => {
        this.toast.success(c.archived ? 'Entreprise restaurée' : 'Entreprise archivée');
        this.load();
      },
      error: (err) => this.toast.error(err)
    });
  }

  remove(): void {
    const c = this.data()!.company;
    if (!confirm(`Supprimer définitivement « ${c.name} » ? Cette action est irréversible.`)) return;
    this.api.delete(c.id).subscribe({
      next: () => {
        this.toast.success('Entreprise supprimée');
        this.router.navigate(['/companies']);
      },
      error: (err) => this.toast.error(err)
    });
  }
}
