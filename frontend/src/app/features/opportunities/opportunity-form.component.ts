import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CompanyApi, UserApi } from '../../core/api.service';
import { Company, Contact, OpportunityStage, UserSummary } from '../../core/models';
import { OpportunityApi } from '../../core/sales.api';
import { STAGE_LABELS } from '../../core/sales.models';
import { ToastService } from '../../core/toast.service';
import { LossDialogComponent } from '../../shared/loss-dialog.component';
import { entries } from '../../shared/ui';

@Component({
  selector: 'app-opportunity-form',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, LossDialogComponent],
  template: `
    <a [routerLink]="id() ? ['/opportunities', id()] : '/pipeline'" class="text-sm text-brand-600 hover:underline">← Retour</a>
    <h1 class="mt-2">{{ id() ? 'Modifier l’opportunité' : 'Nouvelle opportunité' }}</h1>

    <form [formGroup]="form" (ngSubmit)="save()" class="card mt-6 max-w-4xl space-y-6 p-6">
      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div class="sm:col-span-2"><label class="label" for="ti">Titre *</label><input id="ti" class="input" formControlName="title" /></div>
        <div>
          <label class="label" for="co">Entreprise *</label>
          <select id="co" class="input" formControlName="companyId" (change)="loadContacts()">
            <option [ngValue]="null" disabled>Choisir…</option>
            @for (c of companies(); track c.id) { <option [ngValue]="c.id">{{ c.name }}</option> }
          </select>
        </div>
        <div>
          <label class="label" for="ct">Contact</label>
          <select id="ct" class="input" formControlName="contactId">
            <option [ngValue]="null">—</option>
            @for (c of contacts(); track c.id) { <option [ngValue]="c.id">{{ c.fullName }}</option> }
          </select>
        </div>
        <div><label class="label" for="am">Montant HT (MAD) *</label><input id="am" type="number" min="0" step="1000" class="input" formControlName="amount" /></div>
        <div>
          <label class="label" for="st">Étape</label>
          <select id="st" class="input" formControlName="stage" (change)="onStage()">
            @for (s of stages; track s[0]) { <option [value]="s[0]">{{ s[1] }}</option> }
          </select>
        </div>
        <div><label class="label" for="pr">Probabilité (%)</label><input id="pr" type="number" min="0" max="100" class="input" formControlName="probability" /></div>
        <div><label class="label" for="cd">Date de clôture prévue</label><input id="cd" type="date" class="input" formControlName="expectedCloseDate" /></div>
        <div>
          <label class="label" for="ow">Commercial</label>
          <select id="ow" class="input" formControlName="ownerId">
            <option [ngValue]="null">— Moi —</option>
            @for (u of owners(); track u.id) { <option [ngValue]="u.id">{{ u.fullName }}</option> }
          </select>
        </div>
        <div class="sm:col-span-2"><label class="label" for="de">Description</label><textarea id="de" rows="3" class="input" formControlName="description"></textarea></div>
      </div>
      <div class="flex justify-end gap-2 border-t border-slate-200 pt-4">
        <a [routerLink]="id() ? ['/opportunities', id()] : '/pipeline'" class="btn-secondary">Annuler</a>
        <button type="submit" class="btn-primary" [disabled]="form.invalid || saving()">Enregistrer</button>
      </div>
    </form>

    @if (askLoss()) {
      <app-loss-dialog (cancelled)="askLoss.set(false)" (confirmed)="loss = $event; askLoss.set(false); submit()" />
    }
  `
})
export class OpportunityFormComponent implements OnInit {
  private api = inject(OpportunityApi);
  private companyApi = inject(CompanyApi);
  private users = inject(UserApi);
  private router = inject(Router);
  private toast = inject(ToastService);

  id = input<string>();
  companyId = input<string>();
  stages = entries(STAGE_LABELS);
  defaults: Record<OpportunityStage, number> = { PROSPECTION: 10, QUALIFICATION: 25, PROPOSITION: 50, NEGOCIATION: 75, GAGNEE: 100, PERDUE: 0 };
  companies = signal<Company[]>([]);
  contacts = signal<Contact[]>([]);
  owners = signal<UserSummary[]>([]);
  saving = signal(false);
  askLoss = signal(false);
  loss: { lossReason: string; lossComment: string } | null = null;
  private initialStage: OpportunityStage | null = null;

  form = inject(FormBuilder).group({
    title: ['', Validators.required],
    companyId: [null as number | null, Validators.required],
    contactId: [null as number | null],
    amount: [0 as number | null, [Validators.required, Validators.min(0)]],
    stage: ['PROSPECTION' as OpportunityStage],
    probability: [10 as number | null],
    expectedCloseDate: [''],
    ownerId: [null as number | null],
    description: ['']
  });

  ngOnInit(): void {
    this.companyApi.search({ size: 100, sort: 'name' }).subscribe((p) => this.companies.set(p.content));
    this.users.assignable().subscribe((u) => this.owners.set(u));
    if (this.companyId()) {
      this.form.patchValue({ companyId: +this.companyId()! });
      this.loadContacts();
    }
    if (this.id()) {
      this.api.get(+this.id()!).subscribe((o) => {
        this.initialStage = o.stage;
        this.form.patchValue({
          ...o,
          companyId: o.company.id,
          contactId: o.contact?.id ?? null,
          ownerId: o.owner?.id ?? null,
          expectedCloseDate: o.expectedCloseDate ?? ''
        });
        this.loadContacts();
      });
    }
  }

  loadContacts(): void {
    const companyId = this.form.controls.companyId.value;
    if (!companyId) return;
    this.companyApi.get(companyId).subscribe((d) => this.contacts.set(d.contacts));
  }

  onStage(): void {
    this.form.patchValue({ probability: this.defaults[this.form.controls.stage.value!] });
  }

  save(): void {
    if (this.form.controls.stage.value === 'PERDUE' && this.initialStage !== 'PERDUE' && !this.loss) {
      this.askLoss.set(true);
      return;
    }
    this.submit();
  }

  submit(): void {
    this.saving.set(true);
    const v = this.form.getRawValue();
    const body = { ...v, expectedCloseDate: v.expectedCloseDate || null, ...(this.loss ?? {}) };
    const id = this.id();
    (id ? this.api.update(+id, body) : this.api.create(body)).subscribe({
      next: (o) => {
        this.toast.success(id ? 'Opportunité mise à jour' : 'Opportunité créée');
        this.router.navigate(['/opportunities', o.id]);
      },
      error: (err) => {
        this.toast.error(err);
        this.saving.set(false);
      }
    });
  }
}
