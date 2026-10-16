import { Location } from '@angular/common';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { CompanyApi, ContactApi, UserApi } from '../../core/api.service';
import { Company, ContactRequest, UserSummary } from '../../core/models';
import { ToastService, fieldErrors } from '../../core/toast.service';
import { TagInputComponent } from '../../shared/tag-input.component';

@Component({
  selector: 'app-contact-form',
  standalone: true,
  imports: [ReactiveFormsModule, TagInputComponent],
  template: `
    <button class="text-sm text-brand-600 hover:underline" (click)="location.back()">← Retour</button>
    <h1 class="mt-2">{{ id() ? 'Modifier le contact' : 'Nouveau contact' }}</h1>

    <form [formGroup]="form" (ngSubmit)="save()" class="card mt-6 max-w-3xl space-y-6 p-6">
      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div>
          <label class="label" for="firstName">Prénom *</label>
          <input id="firstName" class="input" formControlName="firstName" />
          @if (errors()['firstName']) { <p class="field-error">{{ errors()['firstName'] }}</p> }
        </div>
        <div>
          <label class="label" for="lastName">Nom *</label>
          <input id="lastName" class="input" formControlName="lastName" />
          @if (errors()['lastName']) { <p class="field-error">{{ errors()['lastName'] }}</p> }
        </div>
        <div>
          <label class="label" for="email">Email</label>
          <input id="email" type="email" class="input" formControlName="email" />
          @if (form.controls.email.invalid && form.controls.email.touched) { <p class="field-error">Email invalide.</p> }
        </div>
        <div>
          <label class="label" for="phone">Téléphone</label>
          <input id="phone" type="tel" class="input" formControlName="phone" />
        </div>
        <div>
          <label class="label" for="position">Poste</label>
          <input id="position" class="input" formControlName="position" />
        </div>
        <div>
          <label class="label" for="company">Entreprise</label>
          <select id="company" class="input" formControlName="companyId">
            <option [ngValue]="null">— Aucune —</option>
            @for (c of companies(); track c.id) { <option [ngValue]="c.id">{{ c.name }}</option> }
          </select>
        </div>
        <div>
          <label class="label" for="owner">Commercial responsable</label>
          <select id="owner" class="input" formControlName="ownerId">
            <option [ngValue]="null">— Moi —</option>
            @for (u of owners(); track u.id) { <option [ngValue]="u.id">{{ u.fullName }}</option> }
          </select>
        </div>
        <label class="flex items-center gap-2 text-sm text-slate-700 sm:col-span-2">
          <input type="checkbox" class="rounded border-slate-300" formControlName="marketingConsent" />
          Consentement aux communications commerciales (loi 09-08) — la date de recueil est enregistrée
        </label>
        <div class="sm:col-span-2">
          <span class="label">Tags</span>
          <app-tag-input [(tags)]="tags" />
        </div>
      </div>
      <div class="flex justify-end gap-2 border-t border-slate-200 pt-4">
        <button type="button" class="btn-secondary" (click)="location.back()">Annuler</button>
        <button type="submit" class="btn-primary" [disabled]="form.invalid || saving()">
          {{ saving() ? 'Enregistrement…' : 'Enregistrer' }}
        </button>
      </div>
    </form>
  `
})
export class ContactFormComponent implements OnInit {
  private api = inject(ContactApi);
  private companyApi = inject(CompanyApi);
  private users = inject(UserApi);
  private router = inject(Router);
  private toast = inject(ToastService);
  location = inject(Location);

  id = input<string>();
  /** Pré-sélection depuis la fiche entreprise : /contacts/new?companyId=3 */
  companyId = input<string>();

  form = inject(FormBuilder).group({
    firstName: ['', [Validators.required, Validators.maxLength(80)]],
    lastName: ['', [Validators.required, Validators.maxLength(80)]],
    email: ['', Validators.email],
    phone: [''],
    position: [''],
    companyId: [null as number | null],
    ownerId: [null as number | null],
    marketingConsent: [false]
  });
  tags = signal<string[]>([]);
  companies = signal<Company[]>([]);
  owners = signal<UserSummary[]>([]);
  saving = signal(false);
  errors = signal<Record<string, string>>({});

  ngOnInit(): void {
    this.companyApi.search({ size: 100, sort: 'name' }).subscribe((p) => this.companies.set(p.content));
    this.users.assignable().subscribe((u) => this.owners.set(u));
    if (this.companyId()) {
      this.form.patchValue({ companyId: +this.companyId()! });
    }
    const id = this.id();
    if (id) {
      this.api.get(+id).subscribe((c) => {
        this.form.patchValue({ ...c, ownerId: c.owner?.id ?? null });
        this.tags.set(c.tags);
      });
    }
  }

  save(): void {
    this.saving.set(true);
    this.errors.set({});
    const body = { ...this.form.getRawValue(), tags: this.tags() } as ContactRequest;
    const id = this.id();
    (id ? this.api.update(+id, body) : this.api.create(body)).subscribe({
      next: (c) => {
        this.toast.success(id ? 'Contact mis à jour' : 'Contact créé');
        this.router.navigate(c.companyId ? ['/companies', c.companyId] : ['/contacts']);
      },
      error: (err) => {
        this.errors.set(fieldErrors(err));
        this.toast.error(err);
        this.saving.set(false);
      }
    });
  }
}
