import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { Page, TicketPriority } from '../../core/models';
import { FileApi, PortalApi } from '../../core/sales.api';
import { INVOICE_STATUS_LABELS, Invoice, QUOTE_STATUS_LABELS, Quote, TICKET_PRIORITY_LABELS, TICKET_STATUS_LABELS, Ticket } from '../../core/sales.models';
import { ToastService } from '../../core/toast.service';
import { MadPipe, ModalComponent, PillComponent } from '../../shared/ui';

@Component({
  selector: 'app-portal-home',
  standalone: true,
  imports: [RouterLink],
  template: `
    <h1>Bienvenue {{ auth.user()?.firstName }}</h1>
    @if (home(); as h) {
      <p class="mt-1 text-sm text-slate-500">{{ h.companyName }}{{ h.city ? ' · ' + h.city : '' }}</p>
      <div class="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-3">
        <a routerLink="/portal/quotes" class="card p-5 hover:border-brand-300"><p class="text-sm text-slate-500">Devis en attente de réponse</p><p class="mt-2 text-3xl font-semibold">{{ h.openQuotes }}</p></a>
        <a routerLink="/portal/invoices" class="card p-5 hover:border-brand-300"><p class="text-sm text-slate-500">Factures à régler</p><p class="mt-2 text-3xl font-semibold">{{ h.unpaidInvoices }}</p></a>
        <a routerLink="/portal/tickets/new" class="card flex flex-col justify-center p-5 hover:border-brand-300"><p class="font-medium text-brand-700">Besoin d’aide ?</p><p class="text-sm text-slate-500">Ouvrir une demande au support →</p></a>
      </div>
    }
  `
})
export class PortalHomeComponent implements OnInit {
  auth = inject(AuthService);
  private api = inject(PortalApi);
  home = signal<{ companyName: string; city: string | null; openQuotes: number; unpaidInvoices: number } | null>(null);
  ngOnInit(): void {
    this.api.home().subscribe((h) => this.home.set(h));
  }
}

@Component({
  selector: 'app-portal-quotes',
  standalone: true,
  imports: [DatePipe, FormsModule, MadPipe, PillComponent, ModalComponent],
  template: `
    <h1>Mes devis</h1>
    <div class="mt-6 space-y-3">
      @for (q of quotes(); track q.id) {
        <div class="card flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p class="font-medium text-slate-900">Devis {{ q.number }} <app-pill [value]="q.status" [labels]="labels" /></p>
            <p class="text-sm text-slate-500">{{ q.date | date: 'dd/MM/yyyy' }} · valable jusqu’au {{ q.validUntil | date: 'dd/MM/yyyy' }} · {{ q.totalTTC | mad }} TTC</p>
          </div>
          <div class="flex flex-wrap gap-2">
            <button class="btn-secondary" (click)="pdf(q)">Voir le PDF</button>
            @if (q.status === 'ENVOYE') {
              <button class="btn-secondary" (click)="refusing.set(q)">Décliner</button>
              <button class="btn-primary" (click)="accept(q)">Accepter</button>
            }
          </div>
        </div>
      } @empty { <p class="card p-8 text-center text-sm text-slate-500">Aucun devis pour le moment.</p> }
    </div>
    @if (refusing(); as q) {
      <app-modal [title]="'Décliner le devis ' + q.number" [onClose]="closeRefuse">
        <label class="label" for="c">Pouvez-vous nous indiquer pourquoi ? (facultatif)</label>
        <textarea id="c" rows="3" class="input" [(ngModel)]="comment"></textarea>
        <div class="mt-4 flex justify-end gap-2"><button class="btn-secondary" (click)="closeRefuse()">Annuler</button><button class="btn-danger" (click)="refuse(q)">Décliner</button></div>
      </app-modal>
    }
  `
})
export class PortalQuotesComponent implements OnInit {
  private api = inject(PortalApi);
  private files = inject(FileApi);
  private toast = inject(ToastService);
  labels = QUOTE_STATUS_LABELS;
  quotes = signal<Quote[]>([]);
  refusing = signal<Quote | null>(null);
  comment = '';
  closeRefuse = () => this.refusing.set(null);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.quotes().subscribe((q) => this.quotes.set(q));
  }

  pdf(q: Quote): void {
    this.files.openPdf(`/api/portal/quotes/${q.id}/pdf`).subscribe({ error: (err) => this.toast.error(err) });
  }

  accept(q: Quote): void {
    if (!confirm(`Confirmer l’acceptation du devis ${q.number} (${q.totalTTC.toLocaleString('fr-FR')} MAD TTC) ?`)) return;
    this.api.decide(q.id, 'accept').subscribe({ next: () => { this.toast.success('Merci ! Votre commande est enregistrée.'); this.load(); }, error: (err) => this.toast.error(err) });
  }

  refuse(q: Quote): void {
    this.api.decide(q.id, 'refuse', this.comment).subscribe({ next: () => { this.closeRefuse(); this.toast.info('Votre réponse a été transmise.'); this.load(); }, error: (err) => this.toast.error(err) });
  }
}

@Component({
  selector: 'app-portal-invoices',
  standalone: true,
  imports: [DatePipe, MadPipe, PillComponent],
  template: `
    <h1>Mes factures</h1>
    <div class="card mt-6 overflow-x-auto">
      <table class="table">
        <thead class="bg-slate-50"><tr><th>Numéro</th><th>Date</th><th class="hidden sm:table-cell">Échéance</th><th class="text-right">Montant TTC</th><th>Statut</th><th></th></tr></thead>
        <tbody>
          @for (i of invoices(); track i.id) {
            <tr><td class="font-medium">{{ i.number }}</td><td>{{ i.issueDate | date: 'dd/MM/yyyy' }}</td>
              <td class="hidden sm:table-cell" [class.text-red-600]="i.overdue">{{ i.dueDate | date: 'dd/MM/yyyy' }}</td>
              <td class="text-right tabular-nums">{{ i.totalTTC | mad }}</td><td><app-pill [value]="i.status" [labels]="labels" /></td>
              <td class="text-right"><button class="btn-ghost" (click)="pdf(i)">PDF</button></td></tr>
          } @empty { <tr><td colspan="6" class="py-10 text-center text-slate-500">Aucune facture.</td></tr> }
        </tbody>
      </table>
    </div>
  `
})
export class PortalInvoicesComponent implements OnInit {
  private api = inject(PortalApi);
  private files = inject(FileApi);
  private toast = inject(ToastService);
  labels = INVOICE_STATUS_LABELS;
  invoices = signal<Invoice[]>([]);
  ngOnInit(): void {
    this.api.invoices().subscribe((i) => this.invoices.set(i));
  }
  pdf(i: Invoice): void {
    this.files.openPdf(`/api/portal/invoices/${i.id}/pdf`).subscribe({ error: (err) => this.toast.error(err) });
  }
}

@Component({
  selector: 'app-portal-tickets',
  standalone: true,
  imports: [DatePipe, RouterLink, PillComponent],
  template: `
    <div class="flex items-center justify-between"><h1>Mes demandes</h1><a routerLink="/portal/tickets/new" class="btn-primary">+ Nouvelle demande</a></div>
    <div class="mt-6 space-y-3">
      @for (t of page()?.content; track t.id) {
        <a [routerLink]="['/portal/tickets', t.id]" class="card flex flex-col gap-2 p-4 hover:border-brand-300 sm:flex-row sm:items-center sm:justify-between">
          <div><p class="font-medium text-slate-900">{{ t.subject }}</p><p class="text-xs text-slate-500">{{ t.number }} · ouverte le {{ t.openedAt | date: 'short' }}</p></div>
          <app-pill [value]="t.status" [labels]="labels" />
        </a>
      } @empty { <p class="card p-8 text-center text-sm text-slate-500">Aucune demande.</p> }
    </div>
  `
})
export class PortalTicketsComponent implements OnInit {
  private api = inject(PortalApi);
  labels = TICKET_STATUS_LABELS;
  page = signal<Page<Ticket> | null>(null);
  ngOnInit(): void {
    this.api.tickets().subscribe((p) => this.page.set(p));
  }
}

@Component({
  selector: 'app-portal-ticket-new',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <a routerLink="/portal/tickets" class="text-sm text-brand-600 hover:underline">← Mes demandes</a>
    <h1 class="mt-2">Nouvelle demande</h1>
    <form [formGroup]="form" (ngSubmit)="save()" class="card mt-6 space-y-4 p-6">
      <div><label class="label" for="s">Objet *</label><input id="s" class="input" formControlName="subject" /></div>
      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div><label class="label" for="p">Priorité</label><select id="p" class="input" formControlName="priority">
          <option value="BASSE">{{ prio.BASSE }}</option><option value="NORMALE">{{ prio.NORMALE }}</option><option value="HAUTE">{{ prio.HAUTE }}</option></select></div>
        <div><label class="label" for="c">Catégorie</label><select id="c" class="input" formControlName="category">
          <option value="Technique">Problème technique</option><option value="Facturation">Facturation</option><option value="Commande">Commande / livraison</option><option value="Autre">Autre</option></select></div>
      </div>
      <div><label class="label" for="d">Description *</label><textarea id="d" rows="6" class="input" formControlName="description"></textarea></div>
      <div class="flex justify-end"><button class="btn-primary" [disabled]="form.invalid">Envoyer</button></div>
    </form>
  `
})
export class PortalTicketNewComponent {
  private api = inject(PortalApi);
  private router = inject(Router);
  private toast = inject(ToastService);
  prio = TICKET_PRIORITY_LABELS;
  form = inject(FormBuilder).group({
    subject: ['', Validators.required],
    priority: ['NORMALE' as TicketPriority],
    category: ['Technique'],
    description: ['', Validators.required]
  });
  save(): void {
    this.api.openTicket(this.form.getRawValue()).subscribe({
      next: (t) => { this.toast.success(`Demande ${t.number} envoyée`); this.router.navigate(['/portal/tickets', t.id]); },
      error: (err) => this.toast.error(err)
    });
  }
}

@Component({
  selector: 'app-portal-ticket',
  standalone: true,
  imports: [DatePipe, FormsModule, RouterLink, PillComponent],
  template: `
    <a routerLink="/portal/tickets" class="text-sm text-brand-600 hover:underline">← Mes demandes</a>
    @if (t(); as t) {
      <div class="mt-2 flex flex-wrap items-center gap-3"><h1>{{ t.subject }}</h1><app-pill [value]="t.status" [labels]="labels" /></div>
      <p class="text-sm text-slate-500">{{ t.number }} · ouverte le {{ t.openedAt | date: 'short' }}</p>
      <div class="mt-6 space-y-3">
        @if (t.description) { <div class="card p-4 text-sm"><p class="mb-1 text-xs text-slate-500">Vous</p><p class="whitespace-pre-line">{{ t.description }}</p></div> }
        @for (m of t.messages; track m.id) {
          <div class="rounded-xl p-4 text-sm" [class]="m.fromClient ? 'card mr-8' : 'bg-brand-50 ml-8'">
            <p class="mb-1 text-xs text-slate-500">{{ m.fromClient ? 'Vous' : m.author + ' · support' }} · {{ m.createdAt | date: 'short' }}</p>
            <p class="whitespace-pre-line">{{ m.content }}</p>
          </div>
        }
      </div>
      @if (t.status !== 'FERME') {
        <div class="card mt-4 p-4">
          <textarea rows="3" class="input" [(ngModel)]="reply" placeholder="Votre message…"></textarea>
          <div class="mt-2 flex justify-end"><button class="btn-primary" [disabled]="!reply.trim()" (click)="send()">Envoyer</button></div>
        </div>
      }
    }
  `
})
export class PortalTicketComponent implements OnInit {
  private api = inject(PortalApi);
  private toast = inject(ToastService);
  id = input.required<string>();
  labels = TICKET_STATUS_LABELS;
  t = signal<Ticket | null>(null);
  reply = '';
  ngOnInit(): void {
    this.api.ticket(+this.id()).subscribe((t) => this.t.set(t));
  }
  send(): void {
    this.api.reply(+this.id(), this.reply).subscribe({ next: (t) => { this.t.set(t); this.reply = ''; }, error: (err) => this.toast.error(err) });
  }
}
