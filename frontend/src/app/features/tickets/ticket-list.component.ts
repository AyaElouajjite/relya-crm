import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Subject, debounceTime } from 'rxjs';
import { ContactApi } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Contact, Page, TicketPriority } from '../../core/models';
import { TicketApi } from '../../core/sales.api';
import { TICKET_PRIORITY_LABELS, TICKET_STATUS_LABELS, Ticket } from '../../core/sales.models';
import { ToastService } from '../../core/toast.service';
import { HasPermissionDirective } from '../../shared/has-permission.directive';
import { PaginationComponent } from '../../shared/pagination.component';
import { ModalComponent, PillComponent, entries } from '../../shared/ui';

@Component({
  selector: 'app-ticket-list',
  standalone: true,
  imports: [FormsModule, ReactiveFormsModule, DatePipe, PillComponent, PaginationComponent, ModalComponent, HasPermissionDirective],
  template: `
    <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
      <div><h1>Tickets SAV</h1><p class="text-sm text-slate-500">Demandes clients, délais de traitement (SLA) selon la priorité.</p></div>
      <button *appCan="'TICKET_MANAGE'" class="btn-primary" (click)="openCreate()">+ Nouveau ticket</button>
    </div>
    <div class="card mt-6">
      <div class="grid grid-cols-1 gap-3 border-b border-slate-200 p-4 sm:grid-cols-2 lg:grid-cols-5">
        <input class="input lg:col-span-2" placeholder="Rechercher (numéro, objet, client)…" [(ngModel)]="f.q" (ngModelChange)="search$.next()" />
        <select class="input" [(ngModel)]="f.status" (ngModelChange)="load(0)">
          <option value="">Tous les statuts</option>
          @for (s of statuses; track s[0]) { <option [value]="s[0]">{{ s[1] }}</option> }
        </select>
        <select class="input" [(ngModel)]="f.priority" (ngModelChange)="load(0)">
          <option value="">Toutes priorités</option>
          @for (s of priorities; track s[0]) { <option [value]="s[0]">{{ s[1] }}</option> }
        </select>
        <div class="flex items-center gap-4 text-sm text-slate-600">
          <label class="flex items-center gap-1"><input type="checkbox" class="rounded border-slate-300" [(ngModel)]="f.openOnly" (ngModelChange)="load(0)" /> Ouverts</label>
          <label class="flex items-center gap-1"><input type="checkbox" class="rounded border-slate-300" [(ngModel)]="f.mine" (ngModelChange)="load(0)" /> Les miens</label>
        </div>
      </div>
      <div class="overflow-x-auto">
        <table class="table">
          <thead class="bg-slate-50"><tr><th>Ticket</th><th class="hidden md:table-cell">Client</th><th>Priorité</th><th>Statut</th><th class="hidden lg:table-cell">Agent</th><th class="hidden sm:table-cell">Échéance</th></tr></thead>
          <tbody>
            @for (t of page()?.content; track t.id) {
              <tr class="cursor-pointer" (click)="router.navigate(['/tickets', t.id])">
                <td><span class="font-medium text-slate-900">{{ t.subject }}</span><span class="block text-xs text-slate-400">{{ t.number }} · {{ t.openedAt | date: 'short' }}</span></td>
                <td class="hidden md:table-cell">{{ t.company.label }}<span class="block text-xs text-slate-400">{{ t.contact.label }}</span></td>
                <td><app-pill [value]="t.priority" [labels]="priorityLabels" /></td>
                <td><app-pill [value]="t.status" [labels]="statusLabels" /></td>
                <td class="hidden lg:table-cell">{{ t.assignee?.fullName ?? '—' }}</td>
                <td class="hidden sm:table-cell" [class.font-medium]="t.overdue" [class.text-red-600]="t.overdue">
                  {{ t.resolvedAt ? 'Résolu' : (t.dueAt | date: 'dd/MM HH:mm') }}{{ t.overdue ? ' · dépassé' : '' }}
                </td>
              </tr>
            } @empty { <tr><td colspan="6" class="py-10 text-center text-slate-500">Aucun ticket.</td></tr> }
          </tbody>
        </table>
      </div>
      @if (page(); as p) { <app-pagination [page]="p.page" [size]="p.size" [total]="p.totalElements" [totalPages]="p.totalPages" (pageChange)="load($event)" /> }
    </div>

    @if (creating()) {
      <app-modal title="Nouveau ticket" [onClose]="closeCreate">
        <form [formGroup]="form" (ngSubmit)="create()" class="space-y-4">
          <div>
            <label class="label" for="cs">Client (contact) *</label>
            <input id="cs" class="input" placeholder="Rechercher un contact…" [(ngModel)]="contactQuery" [ngModelOptions]="{ standalone: true }" (ngModelChange)="searchContacts()" />
            <select class="input mt-2" formControlName="contactId" size="4">
              @for (c of contacts(); track c.id) { <option [ngValue]="c.id">{{ c.fullName }} — {{ c.companyName ?? 'sans entreprise' }}</option> }
            </select>
          </div>
          <div><label class="label" for="su">Objet *</label><input id="su" class="input" formControlName="subject" /></div>
          <div class="grid grid-cols-2 gap-4">
            <div><label class="label" for="pr">Priorité</label><select id="pr" class="input" formControlName="priority">
              @for (s of priorities; track s[0]) { <option [value]="s[0]">{{ s[1] }}</option> }</select></div>
            <div><label class="label" for="ch">Canal</label><select id="ch" class="input" formControlName="channel">
              <option value="TELEPHONE">Téléphone</option><option value="EMAIL">E-mail</option><option value="AGENT">Autre</option></select></div>
          </div>
          <div><label class="label" for="ca">Catégorie</label><input id="ca" class="input" formControlName="category" placeholder="Technique, Facturation…" /></div>
          <div><label class="label" for="de">Description</label><textarea id="de" rows="4" class="input" formControlName="description"></textarea></div>
          <div class="flex justify-end gap-2">
            <button type="button" class="btn-secondary" (click)="closeCreate()">Annuler</button>
            <button class="btn-primary" [disabled]="form.invalid">Créer</button>
          </div>
        </form>
      </app-modal>
    }
  `
})
export class TicketListComponent implements OnInit {
  private api = inject(TicketApi);
  private contactApi = inject(ContactApi);
  private toast = inject(ToastService);
  private me = inject(AuthService).user()!.id;
  router = inject(Router);

  statuses = entries(TICKET_STATUS_LABELS);
  priorities = entries(TICKET_PRIORITY_LABELS);
  statusLabels = TICKET_STATUS_LABELS;
  priorityLabels = TICKET_PRIORITY_LABELS;
  f = { q: '', status: '', priority: '', openOnly: true, mine: false };
  page = signal<Page<Ticket> | null>(null);
  search$ = new Subject<void>();
  creating = signal(false);
  contacts = signal<Contact[]>([]);
  contactQuery = '';
  closeCreate = () => this.creating.set(false);
  form = inject(FormBuilder).group({
    contactId: [null as number | null, Validators.required],
    subject: ['', Validators.required],
    priority: ['NORMALE' as TicketPriority],
    channel: ['TELEPHONE'],
    category: [''],
    description: ['']
  });

  ngOnInit(): void {
    this.search$.pipe(debounceTime(300)).subscribe(() => this.load(0));
    this.load(0);
  }

  load(page: number): void {
    const { mine, ...rest } = this.f;
    this.api.search({ ...rest, assigneeId: mine ? this.me : null, page, size: 20 }).subscribe({
      next: (p) => this.page.set(p),
      error: (err) => this.toast.error(err)
    });
  }

  openCreate(): void {
    this.form.reset({ priority: 'NORMALE', channel: 'TELEPHONE' });
    this.searchContacts();
    this.creating.set(true);
  }

  searchContacts(): void {
    this.contactApi.search({ q: this.contactQuery, size: 20 }).subscribe((p) => this.contacts.set(p.content));
  }

  create(): void {
    this.api.create(this.form.getRawValue()).subscribe({
      next: (t) => {
        this.toast.success(`Ticket ${t.number} créé`);
        this.router.navigate(['/tickets', t.id]);
      },
      error: (err) => this.toast.error(err)
    });
  }
}
