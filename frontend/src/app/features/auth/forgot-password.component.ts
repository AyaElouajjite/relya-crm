import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { errorMessage } from '../../core/toast.service';
import { AuthCardComponent } from './auth-card.component';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, AuthCardComponent],
  template: `
    <app-auth-card title="Mot de passe oublié" subtitle="Recevez un lien pour choisir un nouveau mot de passe.">
      @if (sent()) {
        <p class="rounded-lg bg-emerald-50 px-3 py-3 text-sm text-emerald-800">
          Si un compte actif correspond à cette adresse, un e-mail contenant un lien de réinitialisation
          (valable 1 heure) vient d’être envoyé.
        </p>
      } @else {
        <form [formGroup]="form" (ngSubmit)="submit()" class="space-y-4">
          <div>
            <label class="label" for="email">Email</label>
            <input id="email" type="email" class="input" formControlName="email" />
          </div>
          @if (error()) { <p class="field-error">{{ error() }}</p> }
          <button type="submit" class="btn-primary w-full" [disabled]="form.invalid || loading()">Envoyer le lien</button>
        </form>
      }
      <a routerLink="/login" class="mt-6 block text-center text-sm text-brand-600 hover:underline">← Retour à la connexion</a>
    </app-auth-card>
  `
})
export class ForgotPasswordComponent {
  private auth = inject(AuthService);
  form = inject(FormBuilder).nonNullable.group({ email: ['', [Validators.required, Validators.email]] });
  loading = signal(false);
  sent = signal(false);
  error = signal<string | null>(null);

  submit(): void {
    this.loading.set(true);
    this.auth.forgotPassword(this.form.getRawValue().email).subscribe({
      next: () => this.sent.set(true),
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      }
    });
  }
}
