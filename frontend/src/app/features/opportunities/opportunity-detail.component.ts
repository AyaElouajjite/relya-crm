import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { OpportunityStage } from '../../core/models';
import { ActivityApi, OpportunityApi, QuoteApi } from '../../core/sales.api';
import {
  ACTIVITY_STATUS_LABELS,
  ACTIVITY_TYPE_LABELS,
  Activity,
  LOSS_REASON_LABELS,
  Opportunity,
  QUOTE_STATUS_LABELS,
  QuoteSummary,
  STAGE_LABELS
} from '../../core/sales.models';
import { ToastService } from '../../core/toast.service';
import { ActivityDialogComponent } from '../../shared/activity-dialog.component';
import { EmailAssistantComponent } from '../../shared/email-assistant.component';
import { LossDialogComponent } from '../../shared/loss-dialog.component';
import { MadPipe, PillComponent } from '../../shared/ui';

@Component({
  selector: 'app-opportunity-detail',
  standalone: true,
  imports: [RouterLink, DatePipe, MadPipe, PillComponent, ActivityDialogComponent, EmailAssistantComponent, LossDialogComponent],
  template: `
    <a routerLink="/pipeline" class="text-sm text-brand-600 hover:underline">← Pipeline</a>
    @if (opp(); as o) {
      <div class="mt-2 flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <h1>{{ o.title }}</h1>
          <p class="mt-1 text-sm text-slate-500">
            <a [routerLink]="['/companies', o.company.id]" class="text-brand-600 hover:underline">{{ o.company.label }}</a>
            @if (o.contact) { · {{ o.contact.label }} } · {{ o.owner?.fullName }}
          </p>
        </div>
        <div class="flex flex-wrap gap-2">
          <a [routerLink]="['/opportunities', o.id, 'edit']" class="btn-secondary">Modifier</a>
          <button class="btn-secondary" (click)="showActivity.set(true)">Planifier</button>
          <button class="btn-secondary" (click)="showAssistant.set(true)">✨ Rédiger une relance</button>
          @if (o.stage !== 'GAGNEE' && o.stage !== 'PERDUE') {
            <a [routerLink]="['/quotes/new']" [queryParams]="{ opportunityId: o.id }" class="btn-primary">+ Devis</a>
          }
        </div>
      </div>

      <!-- Étapes -->
      <div class="mt-6 grid grid-cols-3 gap-1 sm:grid-cols-6">
        @for (s of stages; track s) {
          <button class="rounded-md px-2 py-2 text-xs font-medium transition"
                  [class]="s === o.stage ? (s === 'PERDUE' ? 'bg-red-600 text-white' : s === 'GAGNEE' ? 'bg-emerald-600 text-white' : 'bg-brand-600 text-white')
                    : 'bg-slate-100 text-slate-600 hover:bg-slate-200'"
                  (click)="changeStage(s)">{{ stageLabels[s] }}</button>
        }
      </div>

      <div class="mt-6 grid grid-cols-2 gap-4 lg:grid-cols-4">
        <div class="card p-4"><p class="text-xs text-slate-500">Montant HT</p><p class="mt-1 text-xl font-semibold">{{ o.amount | mad }}</p></div>
        <div class="card p-4"><p class="text-xs text-slate-500">Probabilité</p><p class="mt-1 text-xl font-semibold">{{ o.probability }} %</p></div>
        <div class="card p-4"><p class="text-xs text-slate-500">Montant pondéré</p><p class="mt-1 text-xl font-semibold">{{ o.weightedAmount | mad }}</p></div>
        <div class="card p-4"><p class="text-xs text-slate-500">{{ o.closedAt ? 'Clôturée le' : 'Clôture prévue' }}</p>
          <p class="mt-1 text-xl font-semibold">{{ (o.closedAt ?? o.expectedCloseDate) | date: 'dd/MM/yyyy' }}</p></div>
      </div>
      @if (o.lossReason) {
        <p class="mt-4 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-800">
          <span class="font-semibold">Motif de perte :</span> {{ lossLabels[o.lossReason] }}{{ o.lossComment ? ' — ' + o.lossComment : '' }}
        </p>
      }
      @if (o.description) { <p class="mt-4 whitespace-pre-line text-sm text-slate-700">{{ o.description }}</p> }

      <div class="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-2">
        <section class="card">
          <h2 class="border-b border-slate-200 px-6 py-4">Devis</h2>
          <ul class="divide-y divide-slate-100">
            @for (q of quotes(); track q.id) {
              <li><a [routerLink]="['/quotes', q.id]" class="flex items-center justify-between px-6 py-3 text-sm hover:bg-slate-50">
                <span><span class="font-medium">{{ q.number }}</span> <span class="text-slate-500">· {{ q.date | date: 'dd/MM/yyyy' }}</span></span>
                <span class="flex items-center gap-3">{{ q.totalTTC | mad }} <app-pill [value]="q.status" [labels]="quoteLabels" /></span>
              </a></li>
            } @empty { <li class="px-6 py-8 text-center text-sm text-slate-500">Aucun devis.</li> }
          </ul>
        </section>
        <section class="card">
          <h2 class="border-b border-slate-200 px-6 py-4">Activités et historique</h2>
          <ul class="max-h-[28rem] divide-y divide-slate-100 overflow-y-auto">
            @for (a of activities(); track a.id) {
              <li class="flex items-start gap-3 px-6 py-3 text-sm">
                <span class="badge" [class]="a.type === 'SYSTEME' ? 'bg-slate-100 text-slate-500' : 'bg-brand-50 text-brand-700'">{{ typeLabels[a.type] }}</span>
                <div class="min-w-0 flex-1">
                  <p class="font-medium text-slate-900">{{ a.subject }}</p>
                  @if (a.description) { <p class="text-slate-500">{{ a.description }}</p> }
                  @if (a.type !== 'SYSTEME') { <p class="text-xs" [class.text-red-600]="a.overdue" [class.text-slate-400]="!a.overdue">{{ statusLabels[a.status] }}{{ a.overdue ? ' · en retard' : '' }}</p> }
                </div>
                <span class="whitespace-nowrap text-xs text-slate-400">{{ a.dueAt | date: 'dd/MM HH:mm' }}</span>
              </li>
            } @empty { <li class="px-6 py-8 text-center text-sm text-slate-500">Aucune activité.</li> }
          </ul>
        </section>
      </div>

      @if (showActivity()) {
        <app-activity-dialog [links]="{ opportunityId: o.id, companyId: o.company.id, contactId: o.contact?.id, label: o.title }"
                             (closed)="showActivity.set(false)" (saved)="showActivity.set(false); loadRelated()" />
      }
      @if (showAssistant()) {
        <app-email-assistant [opportunityId]="o.id" (closed)="showAssistant.set(false)" />
      }
      @if (askLoss()) {
        <app-loss-dialog (cancelled)="askLoss.set(false)" (confirmed)="askLoss.set(false); move('PERDUE', $event.lossReason, $event.lossComment)" />
      }
    }
  `
})
export class OpportunityDetailComponent implements OnInit {
  private api = inject(OpportunityApi);
  private quoteApi = inject(QuoteApi);
  private activityApi = inject(ActivityApi);
  private router = inject(Router);
  private toast = inject(ToastService);

  id = input.required<string>();
  opp = signal<Opportunity | null>(null);
  quotes = signal<QuoteSummary[]>([]);
  activities = signal<Activity[]>([]);
  showActivity = signal(false);
  showAssistant = signal(false);
  askLoss = signal(false);
  stages = Object.keys(STAGE_LABELS) as OpportunityStage[];
  stageLabels = STAGE_LABELS;
  lossLabels = LOSS_REASON_LABELS;
  quoteLabels = QUOTE_STATUS_LABELS;
  typeLabels = ACTIVITY_TYPE_LABELS;
  statusLabels = ACTIVITY_STATUS_LABELS;

  ngOnInit(): void {
    this.api.get(+this.id()).subscribe({ next: (o) => this.opp.set(o), error: (err) => { this.toast.error(err); this.router.navigate(['/pipeline']); } });
    this.loadRelated();
  }

  loadRelated(): void {
    this.quoteApi.search({ opportunityId: this.id(), size: 50 }).subscribe((p) => this.quotes.set(p.content));
    this.activityApi.search({ opportunityId: this.id(), includeSystem: true, ascending: false, size: 100 }).subscribe((p) => this.activities.set(p.content));
  }

  changeStage(stage: OpportunityStage): void {
    if (stage === this.opp()!.stage) return;
    if (stage === 'PERDUE') {
      this.askLoss.set(true);
      return;
    }
    this.move(stage);
  }

  move(stage: OpportunityStage, lossReason?: string, lossComment?: string): void {
    this.api.moveStage(+this.id(), { stage, lossReason, lossComment }).subscribe({
      next: (o) => {
        this.opp.set(o);
        this.toast.success(`Étape : ${STAGE_LABELS[stage]}`);
        this.loadRelated();
      },
      error: (err) => this.toast.error(err)
    });
  }
}
