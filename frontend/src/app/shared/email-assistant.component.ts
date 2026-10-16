import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PlatformApi } from '../core/sales.api';
import { errorMessage } from '../core/toast.service';
import { ModalComponent } from './ui';

/**
 * Assistant de rédaction : génère un brouillon d'e-mail de relance à partir du contexte CRM.
 * Le commercial relit et modifie le texte avant de l'envoyer depuis sa messagerie.
 */
@Component({
  selector: 'app-email-assistant',
  standalone: true,
  imports: [FormsModule, ModalComponent],
  template: `
    <app-modal title="Assistant de rédaction" width="max-w-2xl" [onClose]="closeFn">
      <div class="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <div>
          <label class="label" for="purpose">Objectif</label>
          <select id="purpose" class="input" [(ngModel)]="purpose">
            <option value="relance">Relance</option>
            <option value="devis">Relance de devis</option>
            <option value="remerciement">Remerciement</option>
            <option value="prise_de_contact">Prise de contact</option>
          </select>
        </div>
        <div>
          <label class="label" for="tone">Ton</label>
          <select id="tone" class="input" [(ngModel)]="tone">
            <option value="">Standard</option>
            <option value="chaleureux">Chaleureux</option>
            <option value="formel">Formel</option>
            <option value="concis">Très concis</option>
          </select>
        </div>
        <div class="flex items-end">
          <button class="btn-primary w-full" (click)="generate()" [disabled]="loading()">
            {{ loading() ? 'Rédaction…' : draftBody() ? 'Régénérer' : 'Générer' }}
          </button>
        </div>
        <div class="sm:col-span-3">
          <label class="label" for="notes">Précisions (facultatif)</label>
          <input id="notes" class="input" [(ngModel)]="notes" maxlength="500"
                 placeholder="Ex. proposer une démo jeudi, rappeler la remise de 5 %…" />
        </div>
      </div>

      @if (error()) { <p class="mt-4 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{{ error() }}</p> }

      @if (draftBody() !== null) {
        <div class="mt-5 space-y-3">
          <div>
            <label class="label" for="subject">Objet</label>
            <input id="subject" class="input" [(ngModel)]="subject" />
          </div>
          <div>
            <label class="label" for="body">Message</label>
            <textarea id="body" class="input min-h-[14rem]" [ngModel]="draftBody()" (ngModelChange)="draftBody.set($event)"></textarea>
          </div>
          <p class="text-xs text-slate-500">
            {{ generated() ? 'Brouillon rédigé par l’IA (' + model() + ') — relisez-le avant envoi.' : 'Modèle de texte standard (assistant IA non configuré).' }}
          </p>
          <div class="flex flex-wrap justify-end gap-2">
            <button class="btn-secondary" (click)="copy()">Copier</button>
            <a class="btn-primary" [href]="mailto()">Ouvrir dans ma messagerie</a>
          </div>
        </div>
      }
    </app-modal>
  `
})
export class EmailAssistantComponent implements OnInit {
  private api = inject(PlatformApi);

  contactId = input<number | null>(null);
  opportunityId = input<number | null>(null);
  quoteId = input<number | null>(null);
  initialPurpose = input('relance');
  closed = output<void>();
  closeFn = () => this.closed.emit();

  purpose = 'relance';
  tone = '';
  notes = '';
  subject = '';
  to = signal<string | null>(null);
  draftBody = signal<string | null>(null);
  loading = signal(false);
  error = signal<string | null>(null);
  generated = signal(false);
  model = signal('');

  ngOnInit(): void {
    this.purpose = this.initialPurpose();
  }

  generate(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .draftEmail({
        purpose: this.purpose,
        tone: this.tone,
        notes: this.notes,
        contactId: this.contactId(),
        opportunityId: this.opportunityId(),
        quoteId: this.quoteId()
      })
      .subscribe({
        next: (d) => {
          this.subject = d.subject;
          this.draftBody.set(d.body);
          this.to.set(d.to);
          this.generated.set(d.generated);
          this.model.set(d.model);
          this.loading.set(false);
        },
        error: (err) => {
          this.error.set(errorMessage(err, 'L’assistant est indisponible'));
          this.loading.set(false);
        }
      });
  }

  mailto(): string {
    return `mailto:${encodeURIComponent(this.to() ?? '')}?subject=${encodeURIComponent(this.subject)}&body=${encodeURIComponent(this.draftBody() ?? '')}`;
  }

  copy(): void {
    navigator.clipboard?.writeText(`Objet : ${this.subject}\n\n${this.draftBody() ?? ''}`);
  }
}
