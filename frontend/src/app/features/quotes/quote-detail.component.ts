import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { FileApi, QuoteApi } from '../../core/sales.api';
import { QUOTE_STATUS_LABELS, Quote } from '../../core/sales.models';
import { ToastService } from '../../core/toast.service';
import { EmailAssistantComponent } from '../../shared/email-assistant.component';
import { MadPipe, ModalComponent, PillComponent } from '../../shared/ui';

@Component({
  selector: 'app-quote-detail',
  standalone: true,
  imports: [RouterLink, DatePipe, FormsModule, MadPipe, PillComponent, ModalComponent, EmailAssistantComponent],
  template: `
    <a routerLink="/quotes" class="text-sm text-brand-600 hover:underline">← Devis</a>
    @if (quote(); as q) {
      <div class="mt-2 flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <div class="flex flex-wrap items-center gap-3"><h1>Devis {{ q.number }}</h1><app-pill [value]="q.status" [labels]="labels" /></div>
          <p class="mt-1 text-sm text-slate-500">
            <a [routerLink]="['/companies', q.company.id]" class="text-brand-600 hover:underline">{{ q.company.label }}</a> ·
            <a [routerLink]="['/opportunities', q.opportunity.id]" class="text-brand-600 hover:underline">{{ q.opportunity.label }}</a>
            @if (q.contact) { · {{ q.contact.label }} }
          </p>
        </div>
        <div class="flex flex-wrap gap-2">
          <button class="btn-secondary" (click)="pdf()">PDF</button>
          @switch (q.status) {
            @case ('BROUILLON') {
              <a [routerLink]="['/quotes', q.id, 'edit']" class="btn-secondary">Modifier</a>
              <button class="btn-ghost text-red-600" (click)="remove()">Supprimer</button>
              <button class="btn-primary" (click)="openSend()">{{ q.needsApproval ? 'Soumettre à validation' : 'Envoyer au client' }}</button>
            }
            @case ('EN_VALIDATION') {
              @if (canApprove) {
                <button class="btn-secondary" (click)="showReject.set(true)">Demander des modifications</button>
                <button class="btn-primary" (click)="act('approve')">Valider le devis</button>
              } @else { <span class="self-center text-sm text-amber-700">En attente de validation par la direction</span> }
            }
            @case ('VALIDE') {
              <a [routerLink]="['/quotes', q.id, 'edit']" class="btn-secondary">Modifier</a>
              <button class="btn-primary" (click)="openSend()">Envoyer au client</button>
            }
            @case ('ENVOYE') {
              <button class="btn-secondary" (click)="showAssistant.set(true)">✨ Rédiger une relance</button>
              <button class="btn-secondary" (click)="whatsapp()">WhatsApp</button>
              <button class="btn-secondary" (click)="openSend()">Renvoyer</button>
              <button class="btn-danger" (click)="refuse()">Refusé</button>
              <button class="btn-primary" (click)="act('accept')">Accepté</button>
            }
            @default {
              <button class="btn-secondary" (click)="act('duplicate')">Dupliquer (nouvelle version)</button>
            }
          }
        </div>
      </div>

      @if (q.invoice) {
        <p class="mt-4 rounded-lg bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
          Devis accepté : commande enregistrée et facture <a routerLink="/invoices" class="font-semibold underline">{{ q.invoice.label }}</a> émise.
        </p>
      }
      @if (q.status === 'BROUILLON' && q.needsApproval) {
        <p class="mt-4 rounded-lg bg-amber-50 px-4 py-3 text-sm text-amber-800">Montant au-dessus du seuil : ce devis devra être validé par le directeur commercial avant envoi.</p>
      }

      <div class="card mt-6 overflow-x-auto">
        <table class="table">
          <thead class="bg-slate-50"><tr><th>Désignation</th><th class="text-right">Qté</th><th class="text-right">PU HT</th><th class="text-right">Remise</th><th class="text-right">TVA</th><th class="text-right">Total HT</th></tr></thead>
          <tbody>
            @for (l of q.lines; track l.id) {
              <tr><td>{{ l.description }}</td><td class="text-right">{{ l.quantity }}</td><td class="text-right tabular-nums">{{ l.unitPrice | mad }}</td>
                <td class="text-right">{{ l.discount ? l.discount + ' %' : '—' }}</td><td class="text-right">{{ l.vatRate }} %</td><td class="text-right tabular-nums">{{ l.totalHT | mad }}</td></tr>
            }
          </tbody>
        </table>
        <div class="ml-auto w-full max-w-xs space-y-1 p-4 text-sm">
          <div class="flex justify-between"><span class="text-slate-500">Total HT</span><span class="tabular-nums">{{ q.totalHT | mad }}</span></div>
          <div class="flex justify-between"><span class="text-slate-500">TVA</span><span class="tabular-nums">{{ q.totalVAT | mad }}</span></div>
          <div class="flex justify-between border-t border-slate-200 pt-1 text-base font-semibold"><span>Total TTC</span><span class="tabular-nums">{{ q.totalTTC | mad }}</span></div>
        </div>
      </div>

      <dl class="mt-6 grid grid-cols-2 gap-4 text-sm sm:grid-cols-4">
        <div><dt class="text-slate-500">Date</dt><dd>{{ q.date | date: 'dd/MM/yyyy' }}</dd></div>
        <div><dt class="text-slate-500">Valable jusqu’au</dt><dd>{{ q.validUntil | date: 'dd/MM/yyyy' }}</dd></div>
        <div><dt class="text-slate-500">Envoyé le</dt><dd>{{ (q.sentAt | date: 'short') ?? '—' }}</dd></div>
        <div><dt class="text-slate-500">Validé par</dt><dd>{{ q.approvedBy?.fullName ?? '—' }}</dd></div>
      </dl>
      @if (q.notes) { <p class="mt-4 whitespace-pre-line text-sm text-slate-700">{{ q.notes }}</p> }

      @if (showSend()) {
        <app-modal [title]="q.needsApproval ? 'Soumettre à validation' : 'Envoyer le devis'" [onClose]="closeSend">
          @if (q.needsApproval) {
            <p class="text-sm text-slate-600">Le directeur commercial sera notifié. Vous pourrez envoyer le devis une fois validé.</p>
          } @else {
            <div class="space-y-4">
              <div><label class="label" for="to">Destinataire</label><input id="to" type="email" class="input" [(ngModel)]="sendTo" /></div>
              <div><label class="label" for="msg">Message (facultatif — un texte standard est utilisé sinon)</label><textarea id="msg" rows="5" class="input" [(ngModel)]="sendMessage"></textarea></div>
              <p class="text-xs text-slate-500">Le devis est joint en PDF.</p>
            </div>
          }
          <div class="mt-4 flex justify-end gap-2">
            <button class="btn-secondary" (click)="closeSend()">Annuler</button>
            <button class="btn-primary" (click)="send()">{{ q.needsApproval ? 'Soumettre' : 'Envoyer' }}</button>
          </div>
        </app-modal>
      }
      @if (showReject()) {
        <app-modal title="Demander des modifications" [onClose]="closeReject">
          <textarea rows="4" class="input" [(ngModel)]="rejectComment" placeholder="Ex. remise trop importante, revoir la ligne formation…"></textarea>
          <div class="mt-4 flex justify-end gap-2">
            <button class="btn-secondary" (click)="closeReject()">Annuler</button>
            <button class="btn-primary" (click)="act('reject-approval', { comment: rejectComment }); closeReject()">Renvoyer au commercial</button>
          </div>
        </app-modal>
      }
      @if (showAssistant()) {
        <app-email-assistant [quoteId]="q.id" initialPurpose="devis" (closed)="showAssistant.set(false)" />
      }
    }
  `
})
export class QuoteDetailComponent implements OnInit {
  private api = inject(QuoteApi);
  private files = inject(FileApi);
  private router = inject(Router);
  private toast = inject(ToastService);
  canApprove = inject(AuthService).hasPermission('QUOTE_APPROVE');

  id = input.required<string>();
  quote = signal<Quote | null>(null);
  labels = QUOTE_STATUS_LABELS;
  showSend = signal(false);
  showReject = signal(false);
  showAssistant = signal(false);
  sendTo = '';
  sendMessage = '';
  rejectComment = '';
  closeSend = () => this.showSend.set(false);
  closeReject = () => this.showReject.set(false);

  ngOnInit(): void {
    this.api.get(+this.id()).subscribe({ next: (q) => this.quote.set(q), error: (err) => { this.toast.error(err); this.router.navigate(['/quotes']); } });
  }

  pdf(): void {
    this.files.openPdf(`/api/quotes/${this.id()}/pdf`).subscribe({ error: (err) => this.toast.error(err) });
  }

  openSend(): void {
    this.sendTo = this.quote()!.contactEmail ?? '';
    this.showSend.set(true);
  }

  send(): void {
    this.showSend.set(false);
    this.act('send', { to: this.sendTo || null, message: this.sendMessage || null });
  }

  refuse(): void {
    const comment = prompt('Motif du refus (facultatif) :') ?? undefined;
    this.act('refuse', { comment });
  }

  act(action: 'send' | 'approve' | 'reject-approval' | 'accept' | 'refuse' | 'duplicate', body: unknown = {}): void {
    this.api.action(+this.id(), action, body).subscribe({
      next: (q) => {
        const messages: Record<string, string> = {
          send: q.status === 'EN_VALIDATION' ? 'Devis soumis à validation' : 'Devis envoyé au client',
          approve: 'Devis validé',
          'reject-approval': 'Devis renvoyé au commercial',
          accept: 'Devis accepté : facture émise',
          refuse: 'Devis marqué refusé',
          duplicate: `Nouvelle version ${q.number} créée`
        };
        this.toast.success(messages[action]);
        if (action === 'duplicate') {
          this.router.navigate(['/quotes', q.id]).then(() => this.quote.set(q));
        } else {
          this.quote.set(q);
        }
      },
      error: (err) => this.toast.error(err)
    });
  }

  remove(): void {
    if (!confirm('Supprimer ce brouillon ?')) return;
    this.api.delete(+this.id()).subscribe({ next: () => this.router.navigate(['/quotes']), error: (err) => this.toast.error(err) });
  }

  /** Partage du devis par WhatsApp : lien public (jeton secret) vers le PDF. */
  whatsapp(): void {
    const q = this.quote()!;
    const link = `${location.origin}/api/public/quotes/${q.publicToken}/pdf`;
    const text = `Bonjour, voici notre devis ${q.number} (${q.totalTTC.toLocaleString('fr-FR')} MAD TTC) : ${link}`;
    const phone = (q.contactPhone ?? '').replace(/[^\d]/g, '');
    window.open(`https://wa.me/${phone}?text=${encodeURIComponent(text)}`, '_blank', 'noopener');
  }
}
