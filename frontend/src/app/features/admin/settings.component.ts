import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { LeadApi, PlatformApi } from '../../core/sales.api';
import { ToastService } from '../../core/toast.service';

interface Field {
  key: string;
  label: string;
  type?: 'number' | 'textarea';
  hint?: string;
}

/** Paramètres de la plateforme (administrateur). */
@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [FormsModule],
  template: `
    <h1>Paramètres</h1>
    <p class="text-sm text-slate-500">Configuration de la plateforme : en-tête des documents, règles métier et automatisations.</p>

    <div class="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-2">
      @for (g of groups; track g.title) {
        <section class="card space-y-4 p-6">
          <h2>{{ g.title }}</h2>
          @for (f of g.fields; track f.key) {
            <div>
              <label class="label" [for]="f.key">{{ f.label }}</label>
              @if (f.type === 'textarea') {
                <textarea [id]="f.key" rows="3" class="input" [(ngModel)]="values[f.key]"></textarea>
              } @else {
                <input [id]="f.key" class="input" [type]="f.type ?? 'text'" [(ngModel)]="values[f.key]" />
              }
              @if (f.hint) { <p class="mt-1 text-xs text-slate-500">{{ f.hint }}</p> }
            </div>
          }
        </section>
      }
      <section class="card space-y-3 p-6">
        <h2>Actions</h2>
        <div class="flex flex-wrap gap-2">
          <button class="btn-secondary" (click)="runFollowUps()">Lancer les relances maintenant</button>
          <button class="btn-secondary" (click)="runRetention()">Appliquer la durée de conservation</button>
          <button class="btn-secondary" (click)="retrain()">Ré-entraîner le modèle IA</button>
        </div>
        <p class="text-xs text-slate-500">Les relances et la conservation s’exécutent aussi automatiquement chaque jour à 8 h. Le ré-entraînement utilise les leads clôturés du CRM (au moins 20).</p>
        @if (modelInfo()) { <pre class="overflow-x-auto rounded-lg bg-slate-50 p-3 text-xs">{{ modelInfo() }}</pre> }
      </section>
    </div>
    <div class="mt-6 flex justify-end"><button class="btn-primary" (click)="save()">Enregistrer les paramètres</button></div>
  `
})
export class SettingsComponent implements OnInit {
  private api = inject(PlatformApi);
  private leads = inject(LeadApi);
  private toast = inject(ToastService);
  canPrivacy = inject(AuthService).hasPermission('DATA_PRIVACY');
  values: Record<string, string> = {};
  modelInfo = signal<string | null>(null);

  groups: { title: string; fields: Field[] }[] = [
    {
      title: 'Société (en-tête des devis et factures)',
      fields: [
        { key: 'company.name', label: 'Raison sociale' },
        { key: 'company.address', label: 'Adresse' },
        { key: 'company.ice', label: 'ICE' },
        { key: 'company.phone', label: 'Téléphone' },
        { key: 'company.email', label: 'E-mail' }
      ]
    },
    {
      title: 'Devis et factures',
      fields: [
        { key: 'quote.approvalThreshold', label: 'Seuil de validation par le directeur (MAD TTC)', type: 'number', hint: 'Au-delà, le devis doit être validé avant envoi.' },
        { key: 'quote.validityDays', label: 'Durée de validité des devis (jours)', type: 'number' },
        { key: 'invoice.paymentDays', label: 'Délai de paiement des factures (jours)', type: 'number' },
        { key: 'quote.conditions', label: 'Conditions générales imprimées', type: 'textarea' }
      ]
    },
    {
      title: 'Automatisations et données personnelles',
      fields: [
        { key: 'automation.inactivityDays', label: 'Relance si opportunité sans activité depuis (jours)', type: 'number' },
        { key: 'privacy.leadRetentionMonths', label: 'Conservation des leads non convertis (mois)', type: 'number', hint: 'Loi 09-08 : anonymisation automatique au-delà.' }
      ]
    }
  ];

  ngOnInit(): void {
    this.api.settings().subscribe((s) => (this.values = { ...s }));
  }

  save(): void {
    this.api.saveSettings(this.values).subscribe({ next: (s) => { this.values = { ...s }; this.toast.success('Paramètres enregistrés'); }, error: (err) => this.toast.error(err) });
  }

  runFollowUps(): void {
    this.api.runFollowUps().subscribe({ next: (r) => this.toast.success(`${r.created} relance(s) créée(s)`), error: (err) => this.toast.error(err) });
  }

  runRetention(): void {
    this.api.runRetention().subscribe({ next: (r) => this.toast.success(`${r.anonymized} lead(s) anonymisé(s)`), error: (err) => this.toast.error(err) });
  }

  retrain(): void {
    this.leads.retrain().subscribe({
      next: (r) => {
        this.modelInfo.set(JSON.stringify(r, null, 2));
        this.toast.success('Modèle ré-entraîné');
        this.leads.rescoreAll().subscribe();
      },
      error: (err) => this.toast.error(err)
    });
  }
}
