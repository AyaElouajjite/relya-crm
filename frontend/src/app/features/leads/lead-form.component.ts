import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { UserApi } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { LeadSource, LeadStatus, UserSummary } from '../../core/models';
import { LeadApi } from '../../core/sales.api';
import { LEAD_SOURCE_LABELS, LEAD_STATUS_LABELS } from '../../core/sales.models';
import { ToastService, fieldErrors } from '../../core/toast.service';
import { entries } from '../../shared/ui';

@Component({
  selector: 'app-lead-form',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <a [routerLink]="id() ? ['/leads', id()] : '/leads'" class="text-sm text-brand-600 hover:underline">← Retour</a>
    <h1 class="mt-2">{{ id() ? 'Modifier le lead' : 'Nouveau lead' }}</h1>

    <form [formGroup]="form" (ngSubmit)="save()" class="card mt-6 max-w-4xl space-y-6 p-6">
      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div><label class="label" for="fn">Prénom *</label><input id="fn" class="input" formControlName="firstName" /></div>
        <div><label class="label" for="ln">Nom *</label><input id="ln" class="input" formControlName="lastName" /></div>
        <div>
          <label class="label" for="em">E-mail</label><input id="em" type="email" class="input" formControlName="email" />
          @if (errors()['email']) { <p class="field-error">{{ errors()['email'] }}</p> }
        </div>
        <div><label class="label" for="ph">Téléphone</label><input id="ph" type="tel" class="input" formControlName="phone" /></div>
        <div><label class="label" for="co">Entreprise</label><input id="co" class="input" formControlName="companyName" /></div>
        <div><label class="label" for="po">Poste</label><input id="po" class="input" formControlName="position" /></div>
        <div><label class="label" for="se">Secteur</label><input id="se" class="input" formControlName="sector" /></div>
        <div><label class="label" for="ci">Ville</label><input id="ci" class="input" formControlName="city" /></div>
        <div>
          <label class="label" for="so">Source</label>
          <select id="so" class="input" formControlName="source">
            @for (s of sources; track s[0]) { <option [value]="s[0]">{{ s[1] }}</option> }
          </select>
        </div>
        <div>
          <label class="label" for="st">Statut de qualification</label>
          <select id="st" class="input" formControlName="status">
            @for (s of statuses; track s[0]) { <option [value]="s[0]">{{ s[1] }}</option> }
          </select>
        </div>
        <div>
          <label class="label" for="bu">Budget estimé (MAD)</label>
          <input id="bu" type="number" min="0" step="1000" class="input" formControlName="estimatedBudget" />
        </div>
        <div>
          <label class="label" for="as">Attribué à</label>
          <select id="as" class="input" formControlName="assignedToId" [attr.disabled]="canAssign ? null : true">
            <option [ngValue]="null">— {{ canAssign ? 'Non attribué' : 'Moi' }} —</option>
            @for (u of owners(); track u.id) { <option [ngValue]="u.id">{{ u.fullName }}</option> }
          </select>
        </div>
        <div class="sm:col-span-2"><label class="label" for="no">Notes</label><textarea id="no" rows="3" class="input" formControlName="notes"></textarea></div>
        <label class="flex items-center gap-2 text-sm text-slate-700 sm:col-span-2">
          <input type="checkbox" class="rounded border-slate-300" formControlName="marketingConsent" />
          La personne accepte de recevoir des communications commerciales (consentement loi 09-08)
        </label>
      </div>
      <div class="flex justify-end gap-2 border-t border-slate-200 pt-4">
        <a [routerLink]="id() ? ['/leads', id()] : '/leads'" class="btn-secondary">Annuler</a>
        <button type="submit" class="btn-primary" [disabled]="form.invalid || saving()">Enregistrer</button>
      </div>
    </form>
  `
})
export class LeadFormComponent implements OnInit {
  private api = inject(LeadApi);
  private users = inject(UserApi);
  private router = inject(Router);
  private toast = inject(ToastService);
  canAssign = inject(AuthService).hasPermission('LEAD_ASSIGN');

  id = input<string>();
  sources = entries(LEAD_SOURCE_LABELS);
  statuses = entries(LEAD_STATUS_LABELS).filter(([k]) => k !== 'CONVERTI');
  owners = signal<UserSummary[]>([]);
  saving = signal(false);
  errors = signal<Record<string, string>>({});

  form = inject(FormBuilder).group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    email: ['', Validators.email],
    phone: [''],
    companyName: [''],
    position: [''],
    sector: [''],
    city: [''],
    source: ['MANUEL' as LeadSource],
    status: ['NOUVEAU' as LeadStatus],
    estimatedBudget: [null as number | null],
    assignedToId: [null as number | null],
    notes: [''],
    marketingConsent: [false]
  });

  ngOnInit(): void {
    this.users.assignable().subscribe((u) => this.owners.set(u));
    if (this.id()) {
      this.api.get(+this.id()!).subscribe((l) => this.form.patchValue({ ...l, assignedToId: l.assignedTo?.id ?? null }));
    }
  }

  save(): void {
    this.saving.set(true);
    const id = this.id();
    const body = this.form.getRawValue();
    (id ? this.api.update(+id, body) : this.api.create(body)).subscribe({
      next: (l) => {
        this.toast.success(id ? 'Lead mis à jour' : `Lead créé — score estimé ${l.score ?? '—'} %`);
        this.router.navigate(['/leads', l.id]);
      },
      error: (err) => {
        this.errors.set(fieldErrors(err));
        this.toast.error(err);
        this.saving.set(false);
      }
    });
  }
}
