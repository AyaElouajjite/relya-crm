import { CdkDrag, CdkDragDrop, CdkDropList, CdkDropListGroup } from '@angular/cdk/drag-drop';
import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subject, debounceTime } from 'rxjs';
import { UserApi } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { LossReason, OpportunityStage, UserSummary } from '../../core/models';
import { OpportunityApi } from '../../core/sales.api';
import { Opportunity, PipelineColumn, STAGE_LABELS } from '../../core/sales.models';
import { ToastService } from '../../core/toast.service';
import { LossDialogComponent } from '../../shared/loss-dialog.component';
import { MadPipe } from '../../shared/ui';

const STAGE_ACCENT: Record<OpportunityStage, string> = {
  PROSPECTION: 'border-t-slate-400',
  QUALIFICATION: 'border-t-sky-500',
  PROPOSITION: 'border-t-brand-500',
  NEGOCIATION: 'border-t-amber-500',
  GAGNEE: 'border-t-emerald-500',
  PERDUE: 'border-t-red-500'
};

/** Module 4 : vue Kanban du pipeline ; glisser une carte change son étape. */
@Component({
  selector: 'app-pipeline',
  standalone: true,
  imports: [CdkDropListGroup, CdkDropList, CdkDrag, RouterLink, FormsModule, DatePipe, MadPipe, LossDialogComponent],
  template: `
    <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <h1>Pipeline</h1>
        <p class="text-sm text-slate-500">
          {{ openCount() }} opportunités ouvertes · {{ openTotal() | mad: true }} · pondéré {{ openWeighted() | mad: true }}
        </p>
      </div>
      <div class="flex flex-wrap gap-2">
        <input class="input w-56" placeholder="Rechercher…" [(ngModel)]="q" (ngModelChange)="search$.next()" />
        @if (canSeeAll) {
          <select class="input w-48" [(ngModel)]="ownerId" (ngModelChange)="load()">
            <option value="">Tous les commerciaux</option>
            @for (u of owners(); track u.id) { <option [value]="u.id">{{ u.fullName }}</option> }
          </select>
        }
        <a routerLink="/opportunities/new" class="btn-primary">+ Opportunité</a>
      </div>
    </div>

    <div class="-mx-4 mt-6 overflow-x-auto px-4 pb-4 sm:-mx-6 sm:px-6" cdkDropListGroup>
      <div class="flex min-w-max gap-4">
        @for (col of columns(); track col.stage) {
          <section class="flex w-72 flex-col rounded-xl border border-t-4 border-slate-200 bg-slate-100/70" [class]="accent[col.stage]">
            <header class="px-3 pb-2 pt-3">
              <div class="flex items-center justify-between">
                <h2 class="text-sm">{{ labels[col.stage] }}</h2>
                <span class="badge bg-white text-slate-600">{{ col.count }}</span>
              </div>
              <p class="mt-0.5 text-xs text-slate-500">{{ col.total | mad: true }} @if (col.stage !== 'GAGNEE' && col.stage !== 'PERDUE') { · pondéré {{ col.weighted | mad: true }} }</p>
            </header>
            <div class="flex min-h-[8rem] flex-1 flex-col gap-2 px-2 pb-3" cdkDropList [cdkDropListData]="col" (cdkDropListDropped)="drop($event)">
              @for (o of col.items; track o.id) {
                <a cdkDrag [cdkDragData]="o" [routerLink]="['/opportunities', o.id]"
                   class="block cursor-grab rounded-lg border border-slate-200 bg-white p-3 text-sm shadow-sm hover:border-brand-300 active:cursor-grabbing">
                  <p class="font-medium text-slate-900">{{ o.title }}</p>
                  <p class="text-xs text-slate-500">{{ o.company.label }}</p>
                  <div class="mt-2 flex items-center justify-between text-xs">
                    <span class="font-semibold text-slate-800">{{ o.amount | mad: true }}</span>
                    <span class="text-slate-500">{{ o.probability }} %</span>
                  </div>
                  <div class="mt-1 flex items-center justify-between text-xs text-slate-400">
                    <span>{{ o.owner?.fullName }}</span>
                    @if (o.expectedCloseDate && !o.closedAt) {
                      <span [class.text-red-600]="isLate(o)">{{ o.expectedCloseDate | date: 'dd/MM' }}</span>
                    }
                  </div>
                </a>
              } @empty {
                <p class="py-6 text-center text-xs text-slate-400">Déposez une carte ici</p>
              }
            </div>
          </section>
        }
      </div>
    </div>

    @if (pendingLoss(); as p) {
      <app-loss-dialog (cancelled)="pendingLoss.set(null); load()" (confirmed)="confirmLoss($event)" />
    }
  `
})
export class PipelineComponent implements OnInit {
  private api = inject(OpportunityApi);
  private users = inject(UserApi);
  private toast = inject(ToastService);
  canSeeAll = inject(AuthService).hasPermission('DASHBOARD_ALL');

  labels = STAGE_LABELS;
  accent = STAGE_ACCENT;
  columns = signal<PipelineColumn[]>([]);
  owners = signal<UserSummary[]>([]);
  pendingLoss = signal<{ opp: Opportunity } | null>(null);
  q = '';
  ownerId = '';
  search$ = new Subject<void>();

  private open = computed(() => this.columns().filter((c) => c.stage !== 'GAGNEE' && c.stage !== 'PERDUE'));
  openCount = computed(() => this.open().reduce((s, c) => s + c.count, 0));
  openTotal = computed(() => this.open().reduce((s, c) => s + c.total, 0));
  openWeighted = computed(() => this.open().reduce((s, c) => s + c.weighted, 0));

  ngOnInit(): void {
    this.search$.pipe(debounceTime(300)).subscribe(() => this.load());
    this.users.assignable().subscribe((u) => this.owners.set(u));
    this.load();
  }

  load(): void {
    this.api.pipeline({ q: this.q, ownerId: this.ownerId }).subscribe({
      next: (c) => this.columns.set(c),
      error: (err) => this.toast.error(err)
    });
  }

  isLate(o: Opportunity): boolean {
    return !!o.expectedCloseDate && new Date(o.expectedCloseDate) < new Date(new Date().toDateString());
  }

  drop(event: CdkDragDrop<PipelineColumn>): void {
    const opp = event.item.data as Opportunity;
    const target = event.container.data.stage;
    if (event.previousContainer === event.container || opp.stage === target) return;
    // Déplacement optimiste, annulé si l'API refuse.
    this.columns.update((cols) =>
      cols.map((c) => {
        if (c.stage === opp.stage) return { ...c, items: c.items.filter((i) => i.id !== opp.id), count: c.count - 1 };
        if (c.stage === target) return { ...c, items: [{ ...opp, stage: target }, ...c.items], count: c.count + 1 };
        return c;
      })
    );
    if (target === 'PERDUE') {
      this.pendingLoss.set({ opp });
      return;
    }
    this.move(opp, target);
  }

  confirmLoss(e: { lossReason: LossReason; lossComment: string }): void {
    const opp = this.pendingLoss()!.opp;
    this.pendingLoss.set(null);
    this.move(opp, 'PERDUE', e.lossReason, e.lossComment);
  }

  private move(opp: Opportunity, stage: OpportunityStage, lossReason?: LossReason, lossComment?: string): void {
    this.api.moveStage(opp.id, { stage, lossReason, lossComment }).subscribe({
      next: () => {
        this.toast.success(`« ${opp.title} » → ${STAGE_LABELS[stage]}`);
        this.load();
      },
      error: (err) => {
        this.toast.error(err);
        this.load();
      }
    });
  }
}
