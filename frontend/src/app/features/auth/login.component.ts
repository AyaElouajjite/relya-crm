import { Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { errorMessage } from '../../core/toast.service';
import { AuthCardComponent } from './auth-card.component';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, AuthCardComponent],
  template: `
    <app-auth-card title="Bon retour parmi nous" subtitle="Connectez-vous pour accéder à votre espace.">
      <form [formGroup]="form" (ngSubmit)="submit()" class="space-y-5">
        <div>
          <label class="label" for="email">Adresse e-mail</label>
          <input id="email" type="email" class="input h-10" formControlName="email" autocomplete="username" placeholder="prenom.nom@entreprise.ma" />
        </div>
        <div>
          <div class="flex items-center justify-between">
            <label class="label" for="password">Mot de passe</label>
            <a routerLink="/forgot-password" class="mb-1.5 text-[13px] font-medium text-brand-600 hover:text-brand-700">Mot de passe oublié ?</a>
          </div>
          <div class="relative">
            <input id="password" [type]="showPassword() ? 'text' : 'password'" class="input h-10 pr-10" formControlName="password" autocomplete="current-password" />
            <button type="button" class="absolute inset-y-0 right-0 flex w-10 items-center justify-center text-slate-400 hover:text-slate-600"
                    (click)="showPassword.set(!showPassword())" [attr.aria-label]="showPassword() ? 'Masquer le mot de passe' : 'Afficher le mot de passe'">
              <svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="1.8" viewBox="0 0 24 24">
                @if (showPassword()) {
                  <path d="M3 3l18 18M10.6 10.6a2 2 0 002.8 2.8M9.9 5.1A9.8 9.8 0 0112 5c5 0 9 4.5 10 7a12.6 12.6 0 01-3.2 4.3M6.6 6.6C4.5 8 3 10 2 12c1 2.5 5 7 10 7 1.6 0 3-.4 4.3-1" stroke-linecap="round" stroke-linejoin="round"/>
                } @else {
                  <path d="M2 12c1-2.5 5-7 10-7s9 4.5 10 7c-1 2.5-5 7-10 7S3 14.5 2 12z" stroke-linejoin="round"/><circle cx="12" cy="12" r="3"/>
                }
              </svg>
            </button>
          </div>
        </div>
        @if (error()) {
          <div class="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2.5 text-sm text-red-700" role="alert">
            <svg class="mt-0.5 h-4 w-4 flex-none" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M12 8v4M12 16h.01" stroke-linecap="round"/></svg>
            {{ error() }}
          </div>
        }
        <button type="submit" class="btn-primary h-10 w-full" [disabled]="form.invalid || loading()">
          @if (loading()) {
            <svg class="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-opacity=".3" stroke-width="3"/><path d="M21 12a9 9 0 00-9-9" stroke="currentColor" stroke-width="3" stroke-linecap="round"/></svg>
            Connexion…
          } @else { Se connecter }
        </button>
      </form>
      <p class="mt-8 text-center text-[13px] text-slate-500">
        Pas encore de compte ? Votre administrateur vous en crée un.<br />
        Vous êtes un prospect ? <a routerLink="/contact" class="font-medium text-brand-600 hover:text-brand-700">Contactez-nous</a>
      </p>
    </app-auth-card>
  `
})
export class LoginComponent {
  private auth = inject(AuthService);
  private router = inject(Router);
  returnUrl = input<string>();

  form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required]
  });
  loading = signal(false);
  error = signal<string | null>(null);
  showPassword = signal(false);

  submit(): void {
    if (this.form.invalid) return;
    this.loading.set(true);
    this.error.set(null);
    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).subscribe({
      next: () => this.router.navigateByUrl(this.returnUrl() || '/dashboard'),
      error: (err) => {
        this.error.set(errorMessage(err, 'Connexion impossible'));
        this.loading.set(false);
      }
    });
  }
}
