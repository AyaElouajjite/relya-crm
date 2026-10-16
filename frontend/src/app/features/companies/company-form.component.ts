import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CompanyApi, UserApi } from '../../core/api.service';
import { COMPANY_STATUS_LABELS, CompanyRequest, CompanyStatus, UserSummary } from '../../core/models';
import { ToastService, fieldErrors } from '../../core/toast.service';
import { TagInputComponent } from '../../shared/tag-input.component';

@Component({
  selector: 'app-company-form',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, TagInputComponent],
  template: `
    <a [routerLink]="id() ? ['/companies', id()] : '/companies'" class="text-sm text-brand-600 hover:underline">← Retour</a>
    <h1 class="mt-2">{{ id() ? 'Modifier l’entreprise' : 'Nouvelle entreprise' }}</h1>

    <form [formGroup]="form" (ngSubmit)="save()" class="card mt-6 max-w-4xl space-y-6 p-6">
      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div class="sm:col-span-2">
          <label class="label" for="name">Raison sociale *</label>
          <input id="name" class="input" formControlName="name" />
          @if (errors()['name']) { <p class="field-error">{{ errors()['name'] }}</p> }
        </div>
        <div>
          <label class="label" for="sector">Secteur</label>
          <input id="sector" class="input" formControlName="sector" list="sectors" />
          <datalist id="sectors">@for (s of sectors(); track s) { <option [value]="s"></option> }</datalist>
        </div>
        <div>
          <label class="label" for="ice">ICE</label>
          <input id="ice" class="input" formControlName="ice" maxlength="15" inputmode="numeric" placeholder="15 chiffres" />
          @if (form.controls.ice.invalid && form.controls.ice.touched) {
            <p class="field-error">L’ICE doit contenir exactement 15 chiffres.</p>
          }
          @if (errors()['ice']) { <p class="field-error">{{ errors()['ice'] }}</p> }
        </div>
        <div>
          <label class="label" for="city">Ville</label>
          <input id="city" class="input" formControlName="city" list="cities" />
          <datalist id="cities">@for (c of cities(); track c) { <option [value]="c"></option> }</datalist>
        </div>
        <div>
          <label class="label" for="address">Adresse</label>
          <input id="address" class="input" formControlName="address" />
        </div>
        <div>
          <label class="label" for="phone">Téléphone</label>
          <input id="phone" class="input" formControlName="phone" type="tel" />
        </div>
        <div>
          <label class="label" for="email">Email</label>
          <input id="email" class="input" formControlName="email" type="email" />
          @if (form.controls.email.invalid && form.controls.email.touched) { <p class="field-error">Email invalide.</p> }
        </div>
        <div>
          <label class="label" for="website">Site web</label>
          <input id="website" class="input" formControlName="website" placeholder="https://" />
        </div>
        <div>
          <label class="label" for="status">Statut</label>
          <select id="status" class="input" formControlName="status">
            @for (s of statuses; track s[0]) { <option [value]="s[0]">{{ s[1] }}</option> }
          </select>
        </div>
        <div>
          <label class="label" for="owner">Commercial responsable</label>
          <select id="owner" class="input" formControlName="ownerId">
            <option [ngValue]="null">— Moi —</option>
            @for (u of owners(); track u.id) { <option [ngValue]="u.id">{{ u.fullName }}</option> }
          </select>
        </div>
        <div class="sm:col-span-2">
          <span class="label">Tags</span>
          <app-tag-input [(tags)]="tags" />
        </div>
      </div>

      <div class="flex justify-end gap-2 border-t border-slate-200 pt-4">
        <a [routerLink]="id() ? ['/companies', id()] : '/companies'" class="btn-secondary">Annuler</a>
        <button type="submit" class="btn-primary" [disabled]="form.invalid || saving()">
          {{ saving() ? 'Enregistrement…' : 'Enregistrer' }}
        </button>
      </div>
    </form>
  `
})
export class CompanyFormComponent implements OnInit {
  private api = inject(CompanyApi);
  private users = inject(UserApi);
  private router = inject(Router);
  private toast = inject(ToastService);

  id = input<string>();
  statuses = Object.entries(COMPANY_STATUS_LABELS) as [CompanyStatus, string][];
  form = inject(FormBuilder).group({
    name: ['', [Validators.required, Validators.maxLength(150)]],
    sector: [''],
    ice: ['', Validators.pattern(/^\d{15}$/)],
    city: [''],
    address: [''],
    phone: [''],
    email: ['', Validators.email],
    website: [''],
    status: ['PROSPECT' as CompanyStatus],
    ownerId: [null as number | null]
  });
  tags = signal<string[]>([]);
  owners = signal<UserSummary[]>([]);
  sectors = signal<string[]>([]);
  cities = signal<string[]>([]);
  saving = signal(false);
  errors = signal<Record<string, string>>({});

  ngOnInit(): void {
    this.users.assignable().subscribe((u) => this.owners.set(u));
    this.api.filters().subscribe((f) => {
      this.sectors.set(f.sectors);
      this.cities.set(f.cities);
    });
    const id = this.id();
    if (id) {
      this.api.get(+id).subscribe(({ company: c }) => {
        this.form.patchValue({ ...c, ownerId: c.owner?.id ?? null });
        this.tags.set(c.tags);
      });
    }
  }

  save(): void {
    this.saving.set(true);
    this.errors.set({});
    const body = { ...this.form.getRawValue(), tags: this.tags() } as CompanyRequest;
    const id = this.id();
    (id ? this.api.update(+id, body) : this.api.create(body)).subscribe({
      next: (c) => {
        this.toast.success(id ? 'Entreprise mise à jour' : 'Entreprise créée');
        this.router.navigate(['/companies', c.id]);
      },
      error: (err) => {
        this.errors.set(fieldErrors(err));
        this.toast.error(err);
        this.saving.set(false);
      }
    });
  }
}
