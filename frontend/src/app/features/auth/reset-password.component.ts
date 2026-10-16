import { Component, inject, input, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { ToastService, errorMessage } from '../../core/toast.service';
import { AuthCardComponent } from './auth-card.component';

export function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  return group.get('password')?.value === group.get('confirm')?.value ? null : { mismatch: true };
}

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, AuthCardComponent],
  template: `
    <app-auth-card title="Nouveau mot de passe">
      @if (!token()) {
        <p class="text-sm text-red-600">Lien invalide : le jeton de réinitialisation est absent.</p>
      } @else {
        <form [formGroup]="form" (ngSubmit)="submit()" class="space-y-4">
          <div>
            <label class="label" for="password">Nouveau mot de passe (8 caractères minimum)</label>
            <input id="password" type="password" class="input" formControlName="password" autocomplete="new-password" />
          </div>
          <div>
            <label class="label" for="confirm">Confirmation</label>
            <input id="confirm" type="password" class="input" formControlName="confirm" autocomplete="new-password" />
            @if (form.hasError('mismatch') && form.controls.confirm.touched) {
              <p class="field-error">Les deux mots de passe ne correspondent pas.</p>
            }
          </div>
          @if (error()) { <p class="field-error">{{ error() }}</p> }
          <button type="submit" class="btn-primary w-full" [disabled]="form.invalid || loading()">Enregistrer</button>
        </form>
      }
      <a routerLink="/login" class="mt-6 block text-center text-sm text-brand-600 hover:underline">← Retour à la connexion</a>
    </app-auth-card>
  `
})
export class ResetPasswordComponent {
  private auth = inject(AuthService);
  private router = inject(Router);
  private toast = inject(ToastService);
  token = input<string>();

  form = inject(FormBuilder).nonNullable.group(
    {
      password: ['', [Validators.required, Validators.minLength(8)]],
      confirm: ['', Validators.required]
    },
    { validators: passwordsMatch }
  );
  loading = signal(false);
  error = signal<string | null>(null);

  submit(): void {
    this.loading.set(true);
    this.auth.resetPassword(this.token()!, this.form.getRawValue().password).subscribe({
      next: () => {
        this.toast.success('Mot de passe modifié. Vous pouvez vous connecter.');
        this.router.navigate(['/login']);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      }
    });
  }
}
