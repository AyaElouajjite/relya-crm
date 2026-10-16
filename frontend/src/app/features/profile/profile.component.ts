import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { UserApi } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { LoginLog, Page } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { PaginationComponent } from '../../shared/pagination.component';
import { passwordsMatch } from '../auth/reset-password.component';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [ReactiveFormsModule, DatePipe, PaginationComponent],
  template: `
    <h1>Mon profil</h1>
    <div class="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-2">
      <form [formGroup]="profile" (ngSubmit)="saveProfile()" class="card space-y-4 p-6">
        <h2>Informations</h2>
        <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div><label class="label" for="fn">Prénom</label><input id="fn" class="input" formControlName="firstName" /></div>
          <div><label class="label" for="ln">Nom</label><input id="ln" class="input" formControlName="lastName" /></div>
          <div><label class="label" for="ph">Téléphone</label><input id="ph" class="input" formControlName="phone" /></div>
          <div><span class="label">Rôle</span><p class="py-2 text-sm">{{ auth.user()?.roleLabel }}</p></div>
          <div class="sm:col-span-2"><span class="label">Email</span><p class="text-sm">{{ auth.user()?.email }}</p></div>
        </div>
        <div class="flex justify-end"><button class="btn-primary" [disabled]="profile.invalid">Enregistrer</button></div>
      </form>

      <form [formGroup]="password" (ngSubmit)="changePassword()" class="card space-y-4 p-6">
        <h2>Changer de mot de passe</h2>
        <div><label class="label" for="cur">Mot de passe actuel</label><input id="cur" type="password" class="input" formControlName="current" autocomplete="current-password" /></div>
        <div><label class="label" for="np">Nouveau mot de passe (8 caractères min.)</label><input id="np" type="password" class="input" formControlName="password" autocomplete="new-password" /></div>
        <div>
          <label class="label" for="cf">Confirmation</label><input id="cf" type="password" class="input" formControlName="confirm" autocomplete="new-password" />
          @if (password.hasError('mismatch') && password.controls.confirm.touched) { <p class="field-error">Les mots de passe ne correspondent pas.</p> }
        </div>
        <div class="flex justify-end"><button class="btn-primary" [disabled]="password.invalid">Modifier</button></div>
      </form>
    </div>

    <section class="card mt-6">
      <h2 class="border-b border-slate-200 px-6 py-4">Journal de mes connexions</h2>
      <div class="overflow-x-auto">
        <table class="table">
          <thead class="bg-slate-50"><tr><th>Date</th><th>Résultat</th><th class="hidden sm:table-cell">Adresse IP</th><th class="hidden md:table-cell">Navigateur</th></tr></thead>
          <tbody>
            @for (l of logins()?.content; track l.id) {
              <tr>
                <td>{{ l.at | date: 'medium' }}</td>
                <td><span class="badge" [class]="l.success ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-700'">{{ l.success ? 'Réussie' : 'Échec' }}</span></td>
                <td class="hidden sm:table-cell">{{ l.ipAddress }}</td>
                <td class="hidden max-w-xs truncate md:table-cell" [title]="l.userAgent ?? ''">{{ l.userAgent }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      @if (logins(); as p) {
        <app-pagination [page]="p.page" [size]="p.size" [total]="p.totalElements" [totalPages]="p.totalPages" (pageChange)="loadLogins($event)" />
      }
    </section>
  `
})
export class ProfileComponent implements OnInit {
  auth = inject(AuthService);
  private api = inject(UserApi);
  private toast = inject(ToastService);
  private fb = inject(FormBuilder);

  profile = this.fb.nonNullable.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    phone: ['']
  });
  password = this.fb.nonNullable.group(
    {
      current: ['', Validators.required],
      password: ['', [Validators.required, Validators.minLength(8)]],
      confirm: ['', Validators.required]
    },
    { validators: passwordsMatch }
  );
  logins = signal<Page<LoginLog> | null>(null);

  ngOnInit(): void {
    const u = this.auth.user();
    if (u) this.profile.patchValue({ firstName: u.firstName, lastName: u.lastName, phone: u.phone ?? '' });
    this.loadLogins(0);
  }

  loadLogins(page: number): void {
    this.api.myLogins(page).subscribe((p) => this.logins.set(p));
  }

  saveProfile(): void {
    const v = this.profile.getRawValue();
    this.api.updateProfile({ ...v, phone: v.phone || null }).subscribe({
      next: (u) => {
        this.auth.setUser(u);
        this.toast.success('Profil mis à jour');
      },
      error: (err) => this.toast.error(err)
    });
  }

  changePassword(): void {
    const v = this.password.getRawValue();
    this.api.changePassword(v.current, v.password).subscribe({
      next: (session) => {
        // Les autres sessions sont révoquées ; celle-ci reçoit un nouveau jeton.
        this.auth.storeSession(session);
        this.password.reset();
        this.toast.success('Mot de passe modifié');
      },
      error: (err) => this.toast.error(err)
    });
  }
}
