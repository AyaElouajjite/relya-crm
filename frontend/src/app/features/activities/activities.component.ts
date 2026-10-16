import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { UserApi } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ActivityType, Page, UserSummary } from '../../core/models';
import { ActivityApi } from '../../core/sales.api';
import { ACTIVITY_STATUS_LABELS, ACTIVITY_TYPE_LABELS, Activity } from '../../core/sales.models';
import { ToastService } from '../../core/toast.service';
import { ActivityDialogComponent } from '../../shared/activity-dialog.component';
import { PaginationComponent } from '../../shared/pagination.component';
import { ModalComponent, PillComponent } from '../../shared/ui';

const TYPE_DOT: Record<ActivityType, string> = {
  APPEL: 'bg-sky-500',
  EMAIL: 'bg-violet-500',
  REUNION: 'bg-brand-600',
  TACHE: 'bg-amber-500',
  NOTE: 'bg-slate-400',
  SYSTEME: 'bg-slate-300'
};

interface Day {
  date: Date;
  inMonth: boolean;
  today: boolean;
  items: Activity[];
}

/** Module 6 : mes activités (liste) et calendrier partagé de l'équipe. */
@Component({
  selector: 'app-activities',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe, PillComponent, PaginationComponent, ActivityDialogComponent, ModalComponent],
  template: `
    <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
      <div><h1>Agenda</h1><p class="text-sm text-slate-500">Appels, rendez-vous et tâches ; rappels par notification et e-mail.</p></div>
      <div class="flex flex-wrap gap-2">
        <button class="btn-secondary" (click)="showFeed()">Synchroniser mon agenda</button>
        <button class="btn-primary" (click)="editing.set(null)">+ Planifier</button>
      </div>
    </div>

    <div class="mt-6 flex flex-wrap items-center gap-2">
      <button [class]="tab() === 'list' ? 'btn-primary' : 'btn-secondary'" (click)="tab.set('list')">Mes activités</button>
      <button [class]="tab() === 'calendar' ? 'btn-primary' : 'btn-secondary'" (click)="tab.set('calendar'); loadMonth()">Calendrier</button>
      <select class="input ml-auto w-48" [(ngModel)]="ownerId" (ngModelChange)="reload()">
        <option [ngValue]="me">Moi</option>
        <option [ngValue]="null">Toute l’équipe</option>
        @for (u of owners(); track u.id) { @if (u.id !== me) { <option [ngValue]="u.id">{{ u.fullName }}</option> } }
      </select>
    </div>

    @if (tab() === 'list') {
      <div class="mt-4 flex flex-wrap gap-2 text-sm">
        @for (f of filters; track f.id) {
          <button class="rounded-full px-3 py-1" [class]="filter() === f.id ? 'bg-slate-900 text-white' : 'bg-white text-slate-600 ring-1 ring-slate-200'"
                  (click)="filter.set(f.id); load(0)">{{ f.label }}</button>
        }
      </div>
      <div class="card mt-4">
        <ul class="divide-y divide-slate-100">
          @for (a of page()?.content; track a.id) {
            <li class="flex flex-col gap-2 px-4 py-3 sm:flex-row sm:items-center sm:px-6">
              <div class="flex min-w-0 flex-1 items-start gap-3">
                <input type="checkbox" class="mt-1 rounded border-slate-300" [checked]="a.status === 'TERMINEE'" [disabled]="a.status === 'ANNULEE'"
                       (change)="toggleDone(a)" [attr.aria-label]="'Terminer ' + a.subject" />
                <div class="min-w-0">
                  <p class="font-medium text-slate-900" [class.line-through]="a.status === 'TERMINEE'">
                    <span class="mr-1 inline-block h-2 w-2 rounded-full" [class]="dot[a.type]"></span>{{ typeLabels[a.type] }} · {{ a.subject }}
                  </p>
                  <p class="text-xs text-slate-500">
                    @if (a.company) { <a [routerLink]="['/companies', a.company.id]" class="hover:underline">{{ a.company.label }}</a> }
                    @if (a.opportunity) { · <a [routerLink]="['/opportunities', a.opportunity.id]" class="hover:underline">{{ a.opportunity.label }}</a> }
                    @if (a.lead) { · <a [routerLink]="['/leads', a.lead.id]" class="hover:underline">{{ a.lead.label }}</a> }
                    @if (ownerId !== me) { · {{ a.owner?.fullName }} }
                    @if (a.automatic) { · <span class="text-amber-700">relance automatique</span> }
                  </p>
                </div>
              </div>
              <div class="flex items-center gap-3 pl-7 sm:pl-0">
                <span class="text-sm tabular-nums" [class.text-red-600]="a.overdue" [class.font-medium]="a.overdue">{{ a.dueAt | date: 'EEE d MMM, HH:mm' }}</span>
                <app-pill [value]="a.status" [labels]="statusLabels" />
                <button class="btn-ghost" (click)="editing.set(a)">Modifier</button>
              </div>
            </li>
          } @empty {
            <li class="px-6 py-10 text-center text-sm text-slate-500">Rien à afficher ici.</li>
          }
        </ul>
        @if (page(); as p) { <app-pagination [page]="p.page" [size]="p.size" [total]="p.totalElements" [totalPages]="p.totalPages" (pageChange)="load($event)" /> }
      </div>
    } @else {
      <div class="card mt-4 p-4">
        <div class="mb-4 flex items-center justify-between">
          <button class="btn-ghost" (click)="shift(-1)" aria-label="Mois précédent">‹</button>
          <h2 class="capitalize">{{ month() | date: 'MMMM yyyy' }}</h2>
          <button class="btn-ghost" (click)="shift(1)" aria-label="Mois suivant">›</button>
        </div>
        <div class="grid grid-cols-7 gap-px overflow-hidden rounded-lg border border-slate-200 bg-slate-200 text-xs">
          @for (d of weekDays; track d) { <div class="bg-slate-50 py-2 text-center font-semibold text-slate-500">{{ d }}</div> }
          @for (day of days(); track day.date.getTime()) {
            <div class="min-h-[5.5rem] bg-white p-1" [class.bg-slate-50]="!day.inMonth">
              <p class="mb-1 text-right" [class]="day.today ? 'font-bold text-brand-600' : day.inMonth ? 'text-slate-700' : 'text-slate-400'">{{ day.date.getDate() }}</p>
              @for (a of day.items.slice(0, 3); track a.id) {
                <button class="mb-0.5 block w-full truncate rounded px-1 py-0.5 text-left hover:bg-slate-100" [title]="a.subject" (click)="editing.set(a)"
                        [class.line-through]="a.status === 'TERMINEE'">
                  <span class="mr-1 inline-block h-1.5 w-1.5 rounded-full" [class]="dot[a.type]"></span>{{ a.dueAt | date: 'HH:mm' }} {{ a.subject }}
                </button>
              }
              @if (day.items.length > 3) { <p class="px-1 text-slate-400">+{{ day.items.length - 3 }} autre(s)</p> }
            </div>
          }
        </div>
        <div class="mt-3 flex flex-wrap gap-4 text-xs text-slate-600">
          @for (t of legend; track t[0]) { <span><span class="mr-1 inline-block h-2 w-2 rounded-full" [class]="dot[t[0]]"></span>{{ t[1] }}</span> }
        </div>
      </div>
    }

    @if (editing() !== undefined) {
      <app-activity-dialog [activity]="editing() ?? null" (closed)="editing.set(undefined)" (saved)="editing.set(undefined); reload()" />
    }
    @if (feedUrl()) {
      <app-modal title="Synchroniser mon agenda" [onClose]="closeFeed">
        <p class="text-sm text-slate-600">Abonnez-vous à ce lien depuis Google Agenda (« Autres agendas › À partir de l’URL »), Outlook ou Calendrier Apple. Vos activités y apparaîtront automatiquement.</p>
        <input class="input mt-3 font-mono text-xs" readonly [value]="feedUrl()" (focus)="$any($event.target).select()" />
        <p class="mt-2 text-xs text-slate-500">Ce lien est personnel : ne le partagez pas.</p>
      </app-modal>
    }
  `
})
export class ActivitiesComponent implements OnInit {
  private api = inject(ActivityApi);
  private users = inject(UserApi);
  private toast = inject(ToastService);
  me = inject(AuthService).user()!.id;

  tab = signal<'list' | 'calendar'>('list');
  filter = signal<'todo' | 'overdue' | 'done' | 'all'>('todo');
  filters = [
    { id: 'todo' as const, label: 'À faire' },
    { id: 'overdue' as const, label: 'En retard' },
    { id: 'done' as const, label: 'Terminées' },
    { id: 'all' as const, label: 'Toutes' }
  ];
  ownerId: number | null = this.me;
  owners = signal<UserSummary[]>([]);
  page = signal<Page<Activity> | null>(null);
  month = signal(new Date(new Date().getFullYear(), new Date().getMonth(), 1));
  monthItems = signal<Activity[]>([]);
  editing = signal<Activity | null | undefined>(undefined);
  feedUrl = signal<string | null>(null);
  closeFeed = () => this.feedUrl.set(null);
  typeLabels = ACTIVITY_TYPE_LABELS;
  statusLabels = ACTIVITY_STATUS_LABELS;
  dot = TYPE_DOT;
  legend = Object.entries(ACTIVITY_TYPE_LABELS).filter(([k]) => k !== 'SYSTEME') as [ActivityType, string][];
  weekDays = ['lun.', 'mar.', 'mer.', 'jeu.', 'ven.', 'sam.', 'dim.'];

  days = computed<Day[]>(() => {
    const first = this.month();
    const start = new Date(first);
    start.setDate(1 - ((first.getDay() + 6) % 7)); // lundi de la première semaine
    const todayKey = new Date().toDateString();
    const result: Day[] = [];
    for (let i = 0; i < 42; i++) {
      const d = new Date(start);
      d.setDate(start.getDate() + i);
      const key = d.toDateString();
      result.push({
        date: d,
        inMonth: d.getMonth() === first.getMonth(),
        today: key === todayKey,
        items: this.monthItems().filter((a) => new Date(a.dueAt).toDateString() === key)
      });
    }
    return result;
  });

  ngOnInit(): void {
    this.users.assignable().subscribe((u) => this.owners.set(u));
    this.load(0);
  }

  reload(): void {
    this.tab() === 'list' ? this.load(0) : this.loadMonth();
  }

  load(page: number): void {
    const f = this.filter();
    const params = {
      ownerId: this.ownerId,
      status: f === 'todo' ? 'A_FAIRE' : f === 'done' ? 'TERMINEE' : null,
      overdueOnly: f === 'overdue',
      ascending: f !== 'done' && f !== 'all',
      page,
      size: 20
    };
    this.api.search(params).subscribe({ next: (p) => this.page.set(p), error: (err) => this.toast.error(err) });
  }

  loadMonth(): void {
    const start = this.days()[0].date;
    const end = new Date(this.days()[41].date);
    end.setDate(end.getDate() + 1);
    this.api.calendar(start.toISOString(), end.toISOString(), this.ownerId).subscribe({
      next: (items) => this.monthItems.set(items),
      error: (err) => this.toast.error(err)
    });
  }

  shift(delta: number): void {
    const m = this.month();
    this.month.set(new Date(m.getFullYear(), m.getMonth() + delta, 1));
    this.loadMonth();
  }

  toggleDone(a: Activity): void {
    this.api.setStatus(a.id, a.status === 'TERMINEE' ? 'A_FAIRE' : 'TERMINEE').subscribe({
      next: () => this.reload(),
      error: (err) => this.toast.error(err)
    });
  }

  showFeed(): void {
    this.api.feed().subscribe((r) => this.feedUrl.set(location.origin + r.path));
  }
}
