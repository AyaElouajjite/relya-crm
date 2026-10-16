import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ChartConfiguration } from 'chart.js';
import { UserApi } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { UserSummary } from '../../core/models';
import { ActivityApi, DashboardApi, FileApi } from '../../core/sales.api';
import {
  ACTIVITY_TYPE_LABELS,
  Activity,
  Dashboard,
  LEAD_SOURCE_LABELS,
  LOSS_REASON_LABELS,
  STAGE_LABELS,
  TICKET_STATUS_LABELS
} from '../../core/sales.models';
import { ToastService } from '../../core/toast.service';
import { ChartComponent, horizontalBars, revenueVsObjective } from '../../shared/chart.component';
import { HasPermissionDirective } from '../../shared/has-permission.directive';
import { MadPipe, toDateInput } from '../../shared/ui';

const MONTHS = ['janv.', 'févr.', 'mars', 'avr.', 'mai', 'juin', 'juil.', 'août', 'sept.', 'oct.', 'nov.', 'déc.'];

/** Module 8 : indicateurs commerciaux et SAV, exports PDF / Excel. */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe, DecimalPipe, MadPipe, ChartComponent, HasPermissionDirective],
  template: `
    <div class="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
      <div>
        <h1>Bonjour {{ auth.user()?.firstName }}</h1>
        <p class="mt-1 text-sm text-slate-500">{{ canSeeAll ? 'Performance commerciale de l’équipe' : 'Votre performance commerciale' }}</p>
      </div>
      @if (canSee) {
        <div class="flex flex-wrap items-end gap-2">
          <div><label class="label" for="from">Du</label><input id="from" type="date" class="input" [(ngModel)]="from" (ngModelChange)="load()" /></div>
          <div><label class="label" for="to">Au</label><input id="to" type="date" class="input" [(ngModel)]="to" (ngModelChange)="load()" /></div>
          @if (canSeeAll) {
            <div><label class="label" for="owner">Commercial</label>
              <select id="owner" class="input" [(ngModel)]="ownerId" (ngModelChange)="load()">
                <option [ngValue]="null">Toute l’équipe</option>
                @for (u of owners(); track u.id) { <option [ngValue]="u.id">{{ u.fullName }}</option> }
              </select></div>
          }
          <ng-container *appCan="'DATA_EXPORT'">
            <button class="btn-secondary" (click)="exportPdf()">PDF</button>
            <button class="btn-secondary" (click)="exportExcel()">Excel</button>
          </ng-container>
        </div>
      }
    </div>

    @if (canSee && d(); as d) {
      <!-- Indicateurs clés -->
      <div class="mt-6 grid grid-cols-2 gap-4 lg:grid-cols-4">
        <div class="card p-5">
          <div class="flex items-center justify-between gap-2"><p class="text-[13px] font-medium text-slate-500">CA réalisé (HT)</p><span class="flex h-8 w-8 flex-none items-center justify-center rounded-lg bg-brand-50 text-brand-600"><svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="1.9" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 1v22M17 5H9.5a3.5 3.5 0 000 7h5a3.5 3.5 0 010 7H6" stroke-linecap="round" stroke-linejoin="round"/></svg></span></div>
          <p class="mt-1 text-2xl font-semibold tracking-tight text-slate-900">{{ d.kpis.revenue | mad: true }}</p>
          @if (d.kpis.objectiveRate !== null) {
            <div class="mt-3 h-1.5 overflow-hidden rounded-full bg-slate-100"><div class="h-full rounded-full bg-brand-600" [style.width.%]="min(d.kpis.objectiveRate, 100)"></div></div>
            <p class="mt-1 text-xs text-slate-500">{{ pct(d.kpis.objectiveRate) }} de l’objectif ({{ d.kpis.objective | mad: true }})</p>
          }
        </div>
        <div class="card p-5">
          <div class="flex items-center justify-between gap-2"><p class="text-[13px] font-medium text-slate-500">CA prévisionnel (pondéré)</p><span class="flex h-8 w-8 flex-none items-center justify-center rounded-lg bg-indigo-50 text-indigo-600"><svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="1.9" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 17l6-6 4 4 8-8M14 7h7v7" stroke-linecap="round" stroke-linejoin="round"/></svg></span></div>
          <p class="mt-1 text-2xl font-semibold tracking-tight text-slate-900">{{ d.kpis.forecast | mad: true }}</p>
          <p class="mt-1 text-xs text-slate-500">{{ d.kpis.openOpportunities }} opportunités ouvertes · {{ d.kpis.pipelineTotal | mad: true }}</p>
        </div>
        <div class="card p-5">
          <div class="flex items-center justify-between gap-2"><p class="text-[13px] font-medium text-slate-500">Conversion lead → client</p><span class="flex h-8 w-8 flex-none items-center justify-center rounded-lg bg-emerald-50 text-emerald-600"><svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="1.9" viewBox="0 0 24 24" aria-hidden="true"><path d="M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2M9 11a4 4 0 100-8 4 4 0 000 8zM17 11l2 2 4-4" stroke-linecap="round" stroke-linejoin="round"/></svg></span></div>
          <p class="mt-1 text-2xl font-semibold tracking-tight text-slate-900">{{ pct(d.kpis.leadConversionRate) }}</p>
          <p class="mt-1 text-xs text-slate-500">{{ d.kpis.leadsConverted }} convertis sur {{ d.kpis.leadsCreated }} leads créés</p>
        </div>
        <div class="card p-5">
          <div class="flex items-center justify-between gap-2"><p class="text-[13px] font-medium text-slate-500">Taux de gain</p><span class="flex h-8 w-8 flex-none items-center justify-center rounded-lg bg-amber-50 text-amber-600"><svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="1.9" viewBox="0 0 24 24" aria-hidden="true"><path d="M8 21h8M12 17v4M7 4h10v5a5 5 0 01-10 0zM17 5h3v2a3 3 0 01-3 3M7 5H4v2a3 3 0 003 3" stroke-linecap="round" stroke-linejoin="round"/></svg></span></div>
          <p class="mt-1 text-2xl font-semibold tracking-tight text-slate-900">{{ pct(d.kpis.winRate) }}</p>
          <p class="mt-1 text-xs text-slate-500">{{ d.kpis.won }} gagnées · {{ d.kpis.lost }} perdues · devis acceptés {{ pct(d.kpis.quoteAcceptanceRate) }}</p>
        </div>
        <a routerLink="/tickets" class="card p-5 hover:border-brand-300">
          <div class="flex items-center justify-between gap-2"><p class="text-[13px] font-medium text-slate-500">Tickets ouverts</p><span class="flex h-8 w-8 flex-none items-center justify-center rounded-lg bg-rose-50 text-rose-600"><svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="1.9" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 22a10 10 0 100-20 10 10 0 000 20zM12 16a4 4 0 100-8 4 4 0 000 8zM4.9 4.9l4.3 4.3M14.8 14.8l4.3 4.3M14.8 9.2l4.3-4.3M4.9 19.1l4.3-4.3" stroke-linecap="round" stroke-linejoin="round"/></svg></span></div>
          <p class="mt-1 text-2xl font-semibold tracking-tight text-slate-900">{{ d.kpis.openTickets }}</p>
          <p class="mt-1 text-xs" [class]="d.kpis.overdueTickets ? 'text-red-600' : 'text-slate-500'">{{ d.kpis.overdueTickets }} hors délai (SLA)</p>
        </a>
        <div class="card p-5">
          <div class="flex items-center justify-between gap-2"><p class="text-[13px] font-medium text-slate-500">Délai moyen de résolution</p><span class="flex h-8 w-8 flex-none items-center justify-center rounded-lg bg-sky-50 text-sky-600"><svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="1.9" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 22a10 10 0 100-20 10 10 0 000 20zM12 6v6l4 2" stroke-linecap="round" stroke-linejoin="round"/></svg></span></div>
          <p class="mt-1 text-2xl font-semibold tracking-tight text-slate-900">{{ d.kpis.avgResolutionHours === null ? '—' : (d.kpis.avgResolutionHours | number: '1.0-1') + ' h' }}</p>
          <p class="mt-1 text-xs text-slate-500">Tickets résolus sur la période</p>
        </div>
        <a routerLink="/activities" class="card p-5 hover:border-brand-300">
          <div class="flex items-center justify-between gap-2"><p class="text-[13px] font-medium text-slate-500">Activités réalisées</p><span class="flex h-8 w-8 flex-none items-center justify-center rounded-lg bg-violet-50 text-violet-600"><svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="1.9" viewBox="0 0 24 24" aria-hidden="true"><path d="M9 11l3 3L22 4M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11" stroke-linecap="round" stroke-linejoin="round"/></svg></span></div>
          <p class="mt-1 text-2xl font-semibold tracking-tight text-slate-900">{{ d.kpis.activitiesDone }}</p>
          <p class="mt-1 text-xs" [class]="d.kpis.overdueActivities ? 'text-red-600' : 'text-slate-500'">{{ d.kpis.overdueActivities }} en retard</p>
        </a>
        <div class="card p-5">
          <div class="flex items-center justify-between gap-2"><p class="text-[13px] font-medium text-slate-500">Devis envoyés</p><span class="flex h-8 w-8 flex-none items-center justify-center rounded-lg bg-slate-100 text-slate-600"><svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="1.9" viewBox="0 0 24 24" aria-hidden="true"><path d="M22 2L11 13M22 2l-7 20-4-9-9-4z" stroke-linecap="round" stroke-linejoin="round"/></svg></span></div>
          <p class="mt-1 text-2xl font-semibold tracking-tight text-slate-900">{{ d.kpis.quotesSent }}</p>
          <p class="mt-1 text-xs text-slate-500">sur la période</p>
        </div>
      </div>

      <!-- Graphiques -->
      <div class="mt-6 grid grid-cols-1 gap-6 xl:grid-cols-3">
        <section class="card p-5 xl:col-span-2">
          <h2>Chiffre d’affaires mensuel {{ year() }}</h2>
          <p class="text-xs text-slate-500">Factures émises (HT) comparées aux objectifs mensuels</p>
          <div class="mt-4"><app-chart [config]="revenueChart()" [height]="280" label="Chiffre d'affaires mensuel et objectif" /></div>
        </section>
        <section class="card p-5">
          <h2>Pipeline par étape</h2>
          <p class="text-xs text-slate-500">Montant des opportunités (MAD)</p>
          <div class="mt-4"><app-chart [config]="stageChart()" [height]="280" label="Pipeline par étape" /></div>
        </section>
        <section class="card p-5">
          <h2>Motifs de perte</h2>
          <p class="text-xs text-slate-500">Nombre d’opportunités perdues</p>
          @if (d.lossReasons.length) { <div class="mt-4"><app-chart [config]="lossChart()" [height]="220" label="Motifs de perte" /></div> }
          @else { <p class="py-12 text-center text-sm text-slate-400">Aucune perte sur la période</p> }
        </section>
        <section class="card p-5">
          <h2>Sources des leads</h2>
          <p class="text-xs text-slate-500">Leads créés sur la période</p>
          @if (d.leadSources.length) { <div class="mt-4"><app-chart [config]="sourceChart()" [height]="220" label="Sources des leads" /></div> }
          @else { <p class="py-12 text-center text-sm text-slate-400">Aucun lead sur la période</p> }
        </section>
        <section class="card p-5">
          <h2>Tickets par statut</h2>
          <p class="text-xs text-slate-500">Ensemble des tickets</p>
          <div class="mt-4"><app-chart [config]="ticketChart()" [height]="220" label="Tickets par statut" /></div>
        </section>
      </div>

      @if (d.sellers.length) {
        <section class="card mt-6 overflow-x-auto">
          <h2 class="border-b border-slate-200 px-6 py-4">Performance par commercial</h2>
          <table class="table">
            <thead class="bg-slate-50"><tr><th>Commercial</th><th class="text-right">CA HT</th><th class="text-right">Objectif</th><th>Atteinte</th>
              <th class="text-right">Prévisionnel</th><th class="text-right">Gagnées / perdues</th><th class="text-right">Conversion leads</th><th class="text-right">Activités</th></tr></thead>
            <tbody>
              @for (s of d.sellers; track s.userId) {
                <tr>
                  <td class="font-medium text-slate-900">{{ s.name }}</td>
                  <td class="text-right tabular-nums">{{ s.revenue | mad }}</td>
                  <td class="text-right tabular-nums">{{ s.objective | mad }}</td>
                  <td class="min-w-[8rem]">
                    <div class="flex items-center gap-2"><div class="h-1.5 flex-1 overflow-hidden rounded-full bg-slate-100"><div class="h-full rounded-full bg-brand-600" [style.width.%]="min(s.objectiveRate ?? 0, 100)"></div></div>
                    <span class="w-12 text-right text-xs tabular-nums">{{ pct(s.objectiveRate) }}</span></div>
                  </td>
                  <td class="text-right tabular-nums">{{ s.forecast | mad }}</td>
                  <td class="text-right">{{ s.won }} / {{ s.lost }}</td>
                  <td class="text-right">{{ pct(s.leadConversionRate) }}</td>
                  <td class="text-right">{{ s.activitiesDone }}</td>
                </tr>
              }
            </tbody>
          </table>
        </section>
      }
    }

    <!-- Mes prochaines activités -->
    @if (upcoming().length) {
      <section class="card mt-6">
        <div class="flex items-center justify-between border-b border-slate-200 px-6 py-4">
          <h2>Mes prochaines activités</h2><a routerLink="/activities" class="text-sm text-brand-600 hover:underline">Agenda</a>
        </div>
        <ul class="divide-y divide-slate-100">
          @for (a of upcoming(); track a.id) {
            <li class="flex items-center justify-between gap-3 px-6 py-3 text-sm">
              <span><span class="text-slate-500">{{ typeLabels[a.type] }} ·</span> {{ a.subject }} @if (a.company) { <span class="text-slate-400">— {{ a.company.label }}</span> }</span>
              <span class="whitespace-nowrap tabular-nums" [class.text-red-600]="a.overdue">{{ a.dueAt | date: 'EEE d MMM, HH:mm' }}</span>
            </li>
          }
        </ul>
      </section>
    }
  `
})
export class DashboardComponent implements OnInit {
  auth = inject(AuthService);
  private api = inject(DashboardApi);
  private users = inject(UserApi);
  private activities = inject(ActivityApi);
  private files = inject(FileApi);
  private toast = inject(ToastService);

  canSee = this.auth.hasPermission('DASHBOARD_VIEW', 'DASHBOARD_ALL');
  canSeeAll = this.auth.hasPermission('DASHBOARD_ALL');
  typeLabels = ACTIVITY_TYPE_LABELS;
  from = toDateInput(new Date(new Date().getFullYear(), 0, 1));
  to = toDateInput(new Date());
  ownerId: number | null = null;
  d = signal<Dashboard | null>(null);
  owners = signal<UserSummary[]>([]);
  upcoming = signal<Activity[]>([]);

  year = computed(() => (this.d() ? new Date(this.d()!.to).getFullYear() : new Date().getFullYear()));
  revenueChart = computed<ChartConfiguration>(() => {
    const m = this.d()?.monthly ?? [];
    return revenueVsObjective(m.map((p) => MONTHS[p.month - 1]), m.map((p) => p.revenue), m.map((p) => p.objective));
  });
  stageChart = computed(() => {
    const s = this.d()?.pipelineByStage ?? [];
    return horizontalBars(s.map((x) => `${STAGE_LABELS[x.key as keyof typeof STAGE_LABELS]} (${x.count})`), s.map((x) => x.amount ?? 0), true);
  });
  lossChart = computed(() => {
    const s = this.d()?.lossReasons ?? [];
    return horizontalBars(s.map((x) => LOSS_REASON_LABELS[x.key as keyof typeof LOSS_REASON_LABELS] ?? x.key), s.map((x) => x.count));
  });
  sourceChart = computed(() => {
    const s = this.d()?.leadSources ?? [];
    return horizontalBars(s.map((x) => LEAD_SOURCE_LABELS[x.key as keyof typeof LEAD_SOURCE_LABELS] ?? x.key), s.map((x) => x.count));
  });
  ticketChart = computed(() => {
    const s = this.d()?.ticketsByStatus ?? [];
    return horizontalBars(s.map((x) => TICKET_STATUS_LABELS[x.key as keyof typeof TICKET_STATUS_LABELS] ?? x.key), s.map((x) => x.count));
  });

  ngOnInit(): void {
    if (this.canSeeAll) this.users.assignable().subscribe((u) => this.owners.set(u));
    if (this.canSee) this.load();
    if (this.auth.hasPermission('ACTIVITY_MANAGE')) {
      this.activities.search({ ownerId: this.auth.user()!.id, status: 'A_FAIRE', ascending: true, size: 6 })
        .subscribe((p) => this.upcoming.set(p.content));
    }
  }

  private params() {
    return { from: this.from, to: this.to, ownerId: this.ownerId };
  }

  load(): void {
    if (!this.from || !this.to) return;
    this.api.get(this.params()).subscribe({ next: (d) => this.d.set(d), error: (err) => this.toast.error(err) });
  }

  exportPdf(): void {
    this.files.openPdf('/api/reports/pdf', this.params()).subscribe({ error: (err) => this.toast.error(err) });
  }

  exportExcel(): void {
    this.files.download('/api/reports/excel', this.params(), 'rapport.xlsx').subscribe({ error: (err) => this.toast.error(err) });
  }

  pct(v: number | null): string {
    return v === null || v === undefined ? '—' : `${v.toLocaleString('fr-FR')} %`;
  }

  min(a: number, b: number): number {
    return Math.min(a, b);
  }
}
