import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subject, debounceTime } from 'rxjs';
import { UserApi } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Page, Role, RoleCode, User } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { PaginationComponent } from '../../shared/pagination.component';
import { RolesComponent } from './roles.component';

@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [FormsModule, ReactiveFormsModule, DatePipe, PaginationComponent, RolesComponent],
  template: `
    <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <h1>Utilisateurs</h1>
        <p class="text-sm text-slate-500">Comptes, rôles et accès à la plateforme.</p>
      </div>
      <button class="btn-primary" (click)="openForm(null)">+ Nouvel utilisateur</button>
    </div>

    <div class="card mt-6">
      <div class="grid grid-cols-1 gap-3 border-b border-slate-200 p-4 sm:grid-cols-3">
        <input class="input sm:col-span-2" placeholder="Rechercher (nom, email)…" [(ngModel)]="q" (ngModelChange)="search$.next()" />
        <select class="input" [(ngModel)]="role" (ngModelChange)="load(0)">
          <option value="">Tous les rôles</option>
          @for (r of roles(); track r.code) { <option [value]="r.code">{{ r.label }}</option> }
        </select>
      </div>
      <div class="overflow-x-auto">
        <table class="table">
          <thead class="bg-slate-50">
            <tr><th>Nom</th><th class="hidden sm:table-cell">Rôle</th><th class="hidden md:table-cell">Dernière connexion</th><th>État</th><th></th></tr>
          </thead>
          <tbody>
            @for (u of page()?.content; track u.id) {
              <tr>
                <td><span class="font-medium text-slate-900">{{ u.fullName }}</span><span class="block text-xs text-slate-500">{{ u.email }}</span></td>
                <td class="hidden sm:table-cell">{{ u.roleLabel }}</td>
                <td class="hidden md:table-cell">{{ u.lastLoginAt ? (u.lastLoginAt | date: 'short') : 'Jamais' }}</td>
                <td>
                  <span class="badge" [class]="u.active ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-500'">
                    {{ u.active ? 'Actif' : 'Désactivé' }}
                  </span>
                </td>
                <td class="whitespace-nowrap text-right">
                  <button class="btn-ghost" (click)="openForm(u)">Modifier</button>
                  @if (u.id !== auth.user()?.id) {
                    <button class="btn-ghost" (click)="toggleActive(u)">{{ u.active ? 'Désactiver' : 'Activer' }}</button>
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      @if (page(); as p) {
        <app-pagination [page]="p.page" [size]="p.size" [total]="p.totalElements" [totalPages]="p.totalPages" (pageChange)="load($event)" />
      }
    </div>

    <app-roles />

    @if (editing() !== undefined) {
      <div class="fixed inset-0 z-40 flex items-center justify-center bg-slate-900/40 p-4" (click)="editing.set(undefined)">
        <form [formGroup]="form" (ngSubmit)="save()" class="card w-full max-w-lg space-y-4 p-6" (click)="$event.stopPropagation()">
          <h2>{{ editing() ? 'Modifier l’utilisateur' : 'Nouvel utilisateur' }}</h2>
          <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div><label class="label" for="fn">Prénom *</label><input id="fn" class="input" formControlName="firstName" /></div>
            <div><label class="label" for="ln">Nom *</label><input id="ln" class="input" formControlName="lastName" /></div>
            <div class="sm:col-span-2"><label class="label" for="em">Email *</label><input id="em" type="email" class="input" formControlName="email" /></div>
            <div><label class="label" for="ph">Téléphone</label><input id="ph" class="input" formControlName="phone" /></div>
            <div>
              <label class="label" for="rl">Rôle *</label>
              <select id="rl" class="input" formControlName="roleCode">
                @for (r of roles(); track r.code) { <option [value]="r.code">{{ r.label }}</option> }
              </select>
            </div>
            <div class="sm:col-span-2">
              <label class="label" for="pw">{{ editing() ? 'Nouveau mot de passe (laisser vide pour ne pas changer)' : 'Mot de passe initial *' }}</label>
              <input id="pw" type="password" class="input" formControlName="password" autocomplete="new-password" />
              @if (form.controls.password.invalid && form.controls.password.touched) {
                <p class="field-error">8 caractères minimum.</p>
              }
            </div>
          </div>
          @if (selectedRole(); as r) {
            <p class="rounded-lg bg-slate-50 p-3 text-xs text-slate-600">
              <span class="font-medium">Permissions :</span> {{ r.permissions.length ? r.permissions.join(', ') : 'aucune (portail client)' }}
            </p>
          }
          <div class="flex justify-end gap-2">
            <button type="button" class="btn-secondary" (click)="editing.set(undefined)">Annuler</button>
            <button type="submit" class="btn-primary" [disabled]="form.invalid || saving()">Enregistrer</button>
          </div>
        </form>
      </div>
    }
  `
})
export class UserListComponent implements OnInit {
  private api = inject(UserApi);
  private toast = inject(ToastService);
  private fb = inject(FormBuilder);
  auth = inject(AuthService);

  q = '';
  role = '';
  page = signal<Page<User> | null>(null);
  roles = signal<Role[]>([]);
  /** undefined = fermé, null = création, User = modification */
  editing = signal<User | null | undefined>(undefined);
  saving = signal(false);
  search$ = new Subject<void>();

  form = this.fb.nonNullable.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    phone: [''],
    roleCode: ['COMMERCIAL' as RoleCode, Validators.required],
    password: ['', Validators.minLength(8)]
  });

  ngOnInit(): void {
    this.api.roles().subscribe((r) => this.roles.set(r));
    this.search$.pipe(debounceTime(300)).subscribe(() => this.load(0));
    this.load(0);
  }

  selectedRole(): Role | undefined {
    return this.roles().find((r) => r.code === this.form.controls.roleCode.value);
  }

  load(pageIndex: number): void {
    this.api.search({ q: this.q, role: this.role, page: pageIndex, size: 20 }).subscribe({
      next: (p) => this.page.set(p),
      error: (err) => this.toast.error(err)
    });
  }

  openForm(user: User | null): void {
    this.form.reset();
    const pw = this.form.controls.password;
    pw.setValidators(user ? [Validators.minLength(8)] : [Validators.required, Validators.minLength(8)]);
    pw.updateValueAndValidity();
    if (user) {
      this.form.patchValue({ ...user, phone: user.phone ?? '' });
    }
    this.editing.set(user);
  }

  save(): void {
    const user = this.editing();
    const value = this.form.getRawValue();
    const body = { ...value, password: value.password || null, active: user ? user.active : true };
    this.saving.set(true);
    (user ? this.api.update(user.id, body) : this.api.create(body)).subscribe({
      next: () => {
        this.toast.success(user ? 'Utilisateur mis à jour' : 'Utilisateur créé');
        this.editing.set(undefined);
        this.saving.set(false);
        this.load(this.page()?.page ?? 0);
      },
      error: (err) => {
        this.toast.error(err);
        this.saving.set(false);
      }
    });
  }

  toggleActive(u: User): void {
    this.api.setActive(u.id, !u.active).subscribe({
      next: () => this.load(this.page()?.page ?? 0),
      error: (err) => this.toast.error(err)
    });
  }
}
