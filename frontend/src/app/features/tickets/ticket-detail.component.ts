import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { UserApi } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { UserSummary } from '../../core/models';
import { TicketApi } from '../../core/sales.api';
import { TICKET_PRIORITY_LABELS, TICKET_STATUS_LABELS, Ticket } from '../../core/sales.models';
import { ToastService } from '../../core/toast.service';
import { PillComponent, entries } from '../../shared/ui';

@Component({
  selector: 'app-ticket-detail',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe, PillComponent],
  template: `
    <a routerLink="/tickets" class="text-sm text-brand-600 hover:underline">← Tickets</a>
    @if (ticket(); as t) {
      <div class="mt-2 flex flex-wrap items-center gap-3">
        <h1>{{ t.subject }}</h1>
        <app-pill [value]="t.priority" [labels]="priorityLabels" />
        <app-pill [value]="t.status" [labels]="statusLabels" />
      </div>
      <p class="mt-1 text-sm text-slate-500">{{ t.number }} ·
        <a [routerLink]="['/companies', t.company.id]" class="text-brand-600 hover:underline">{{ t.company.label }}</a> · {{ t.contact.label }}</p>

      <div class="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-3">
        <section class="card flex flex-col lg:col-span-2">
          <div class="space-y-4 p-6">
            @if (t.description) {
              <div class="rounded-lg bg-slate-50 p-4 text-sm"><p class="mb-1 text-xs text-slate-500">Demande initiale · {{ t.openedAt | date: 'short' }}</p><p class="whitespace-pre-line">{{ t.description }}</p></div>
            }
            @for (m of t.messages; track m.id) {
              <div class="rounded-lg p-4 text-sm" [class]="m.internal ? 'border border-dashed border-amber-300 bg-amber-50' : m.fromClient ? 'bg-slate-50 mr-8' : 'bg-brand-50 ml-8'">
                <p class="mb-1 text-xs text-slate-500">{{ m.author }}{{ m.internal ? ' · note interne' : m.fromClient ? ' · client' : '' }} · {{ m.createdAt | date: 'short' }}</p>
                <p class="whitespace-pre-line">{{ m.content }}</p>
              </div>
            }
          </div>
          @if (canManage) {
            <div class="mt-auto border-t border-slate-200 p-4">
              <textarea rows="3" class="input" [(ngModel)]="reply" placeholder="Répondre au client…"></textarea>
              <div class="mt-2 flex flex-wrap items-center justify-between gap-2">
                <label class="flex items-center gap-2 text-sm text-slate-600"><input type="checkbox" class="rounded border-slate-300" [(ngModel)]="internal" /> Note interne (invisible pour le client)</label>
                <button class="btn-primary" [disabled]="!reply.trim()" (click)="send()">{{ internal ? 'Ajouter la note' : 'Envoyer la réponse' }}</button>
              </div>
            </div>
          }
        </section>

        <section class="card space-y-4 p-6 text-sm">
          <div>
            <label class="label" for="st">Statut</label>
            <select id="st" class="input" [ngModel]="t.status" (ngModelChange)="update({ status: $event })" [disabled]="!canManage">
              @for (s of statuses; track s[0]) { <option [value]="s[0]">{{ s[1] }}</option> }
            </select>
          </div>
          <div>
            <label class="label" for="pr">Priorité</label>
            <select id="pr" class="input" [ngModel]="t.priority" (ngModelChange)="update({ priority: $event })" [disabled]="!canManage">
              @for (s of priorities; track s[0]) { <option [value]="s[0]">{{ s[1] }}</option> }
            </select>
          </div>
          <div>
            <label class="label" for="as">Agent</label>
            <select id="as" class="input" [ngModel]="t.assignee?.id ?? null" (ngModelChange)="update({ assigneeId: $event })" [disabled]="!canManage">
              <option [ngValue]="null" disabled>Non attribué</option>
              @for (a of agents(); track a.id) { <option [ngValue]="a.id">{{ a.fullName }}</option> }
            </select>
          </div>
          <dl class="space-y-2 border-t border-slate-200 pt-4">
            <div class="flex justify-between"><dt class="text-slate-500">Canal</dt><dd>{{ t.channel }}</dd></div>
            <div class="flex justify-between"><dt class="text-slate-500">Catégorie</dt><dd>{{ t.category ?? '—' }}</dd></div>
            <div class="flex justify-between"><dt class="text-slate-500">Ouvert le</dt><dd>{{ t.openedAt | date: 'short' }}</dd></div>
            <div class="flex justify-between"><dt class="text-slate-500">Échéance SLA</dt><dd [class.text-red-600]="t.overdue">{{ t.dueAt | date: 'short' }}</dd></div>
            <div class="flex justify-between"><dt class="text-slate-500">1re réponse</dt><dd>{{ (t.firstResponseAt | date: 'short') ?? '—' }}</dd></div>
            <div class="flex justify-between"><dt class="text-slate-500">Résolu le</dt><dd>{{ (t.resolvedAt | date: 'short') ?? '—' }}</dd></div>
          </dl>
          <p class="text-xs text-slate-500">Le client est notifié (e-mail et portail) à chaque changement de statut ou réponse.</p>
        </section>
      </div>
    }
  `
})
export class TicketDetailComponent implements OnInit {
  private api = inject(TicketApi);
  private users = inject(UserApi);
  private router = inject(Router);
  private toast = inject(ToastService);
  canManage = inject(AuthService).hasPermission('TICKET_MANAGE');

  id = input.required<string>();
  ticket = signal<Ticket | null>(null);
  agents = signal<UserSummary[]>([]);
  statuses = entries(TICKET_STATUS_LABELS);
  priorities = entries(TICKET_PRIORITY_LABELS);
  statusLabels = TICKET_STATUS_LABELS;
  priorityLabels = TICKET_PRIORITY_LABELS;
  reply = '';
  internal = false;

  ngOnInit(): void {
    this.api.get(+this.id()).subscribe({ next: (t) => this.ticket.set(t), error: (err) => { this.toast.error(err); this.router.navigate(['/tickets']); } });
    if (this.canManage) {
      this.users.agents().subscribe({ next: (a) => this.agents.set(a), error: () => {} });
    }
  }

  update(body: Record<string, unknown>): void {
    this.api.update(+this.id(), body).subscribe({
      next: (t) => {
        this.ticket.set(t);
        this.toast.success('Ticket mis à jour — client notifié');
      },
      error: (err) => this.toast.error(err)
    });
  }

  send(): void {
    this.api.reply(+this.id(), this.reply, this.internal).subscribe({
      next: (t) => {
        this.ticket.set(t);
        this.reply = '';
        this.internal = false;
      },
      error: (err) => this.toast.error(err)
    });
  }
}
