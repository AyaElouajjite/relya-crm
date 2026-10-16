import { HttpClient } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { errorMessage } from '../../core/toast.service';
import { AuthCardComponent } from '../auth/auth-card.component';

/**
 * Formulaire web de capture de leads (module 3). Peut être intégré au site vitrine
 * (iframe ou appel direct de POST /api/public/leads).
 */
@Component({
  selector: 'app-lead-capture',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, AuthCardComponent],
  template: `
    <app-auth-card title="Demande de contact" subtitle="Laissez-nous vos coordonnées, un conseiller vous rappelle rapidement.">
      @if (done()) {
        <p class="rounded-lg bg-emerald-50 px-3 py-3 text-sm text-emerald-800">{{ done() }}</p>
      } @else {
        <form [formGroup]="form" (ngSubmit)="submit()" class="space-y-4">
          <div class="grid grid-cols-2 gap-3">
            <div><label class="label" for="fn">Prénom *</label><input id="fn" class="input" formControlName="firstName" autocomplete="given-name" /></div>
            <div><label class="label" for="ln">Nom *</label><input id="ln" class="input" formControlName="lastName" autocomplete="family-name" /></div>
          </div>
          <div><label class="label" for="em">E-mail professionnel *</label><input id="em" type="email" class="input" formControlName="email" autocomplete="email" /></div>
          <div class="grid grid-cols-2 gap-3">
            <div><label class="label" for="ph">Téléphone</label><input id="ph" type="tel" class="input" formControlName="phone" autocomplete="tel" /></div>
            <div><label class="label" for="co">Entreprise</label><input id="co" class="input" formControlName="companyName" autocomplete="organization" /></div>
          </div>
          <div><label class="label" for="ms">Votre besoin</label><textarea id="ms" rows="3" class="input" formControlName="message"></textarea></div>
          <!-- Piège à robots : invisible pour les humains -->
          <input type="text" formControlName="website" tabindex="-1" autocomplete="off" class="absolute -left-[9999px] h-0 w-0 opacity-0" aria-hidden="true" />
          <label class="flex items-start gap-2 text-xs text-slate-600">
            <input type="checkbox" class="mt-0.5 rounded border-slate-300" formControlName="dataProcessingConsent" />
            <span>J’accepte que mes données soient traitées pour répondre à ma demande, conformément à la
              <a routerLink="/confidentialite" class="text-brand-600 underline">politique de confidentialité</a> (loi 09-08). *</span>
          </label>
          <label class="flex items-start gap-2 text-xs text-slate-600">
            <input type="checkbox" class="mt-0.5 rounded border-slate-300" formControlName="marketingConsent" />
            <span>J’accepte de recevoir des informations commerciales (facultatif).</span>
          </label>
          @if (error()) { <p class="field-error">{{ error() }}</p> }
          <button class="btn-primary w-full" [disabled]="form.invalid || loading()">Envoyer ma demande</button>
        </form>
      }
    </app-auth-card>
  `
})
export class LeadCaptureComponent {
  private http = inject(HttpClient);
  form = inject(FormBuilder).nonNullable.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    phone: [''],
    companyName: [''],
    message: [''],
    website: [''],
    dataProcessingConsent: [false, Validators.requiredTrue],
    marketingConsent: [false]
  });
  loading = signal(false);
  done = signal<string | null>(null);
  error = signal<string | null>(null);

  submit(): void {
    this.loading.set(true);
    this.http.post<{ message: string }>('/api/public/leads', this.form.getRawValue()).subscribe({
      next: (r) => this.done.set(r.message),
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      }
    });
  }
}

/** Politique de confidentialité : information des personnes (loi 09-08, art. 5). */
@Component({
  selector: 'app-privacy-policy',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="mx-auto max-w-3xl px-4 py-10 text-sm leading-relaxed text-slate-700">
      <a routerLink="/" class="text-brand-600 hover:underline">← Retour</a>
      <h1 class="mt-4">Protection des données personnelles</h1>
      <p class="mt-2 text-slate-500">Conformément à la loi n° 09-08 relative à la protection des personnes physiques à l’égard du traitement des données à caractère personnel.</p>
      <h2 class="mt-6">Responsable du traitement</h2>
      <p>La société éditrice de ce CRM, dont les coordonnées figurent sur ses devis et factures.</p>
      <h2 class="mt-6">Données collectées et finalités</h2>
      <ul class="ml-5 list-disc">
        <li>Identité et coordonnées professionnelles (nom, e-mail, téléphone, poste, entreprise) : gestion de la relation commerciale, devis, facturation et service après-vente.</li>
        <li>Historique des échanges (appels, rendez-vous, tickets) : suivi de la relation client.</li>
        <li>Préférences de communication : envoi d’informations commerciales, uniquement avec votre consentement, horodaté.</li>
      </ul>
      <h2 class="mt-6">Destinataires</h2>
      <p>Les seules équipes habilitées (commerciaux, direction, support), selon un contrôle d’accès par rôle. Les données ne sont ni vendues ni cédées. L’assistant de rédaction reçoit uniquement le contexte nécessaire à la rédaction d’un brouillon (nom, entreprise, objet).</p>
      <h2 class="mt-6">Durée de conservation</h2>
      <p>Les prospects non convertis sont anonymisés automatiquement après la durée fixée par l’entreprise (24 mois par défaut). Les données clients sont conservées pendant la relation commerciale et les durées légales applicables.</p>
      <h2 class="mt-6">Sécurité</h2>
      <p>Connexions chiffrées (HTTPS), mots de passe hachés (BCrypt), accès authentifiés, journalisation des actions sensibles (création, modification, suppression, export) et sauvegarde quotidienne.</p>
      <h2 class="mt-6">Vos droits</h2>
      <p>Vous disposez d’un droit d’accès, de rectification et d’opposition (y compris à la prospection). Pour l’exercer, contactez-nous : nous pouvons exporter l’ensemble des données vous concernant ou les anonymiser. Vous pouvez également saisir la CNDP (www.cndp.ma).</p>
    </div>
  `
})
export class PrivacyPolicyComponent {}
