import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CompanyApi } from '../../core/api.service';
import { Company } from '../../core/models';
import { ActivityApi, LeadApi, PlatformApi } from '../../core/sales.api';
import { ACTIVITY_TYPE_LABELS, Activity, LEAD_SOURCE_LABELS, LEAD_STATUS_LABELS, Lead } from '../../core/sales.models';
import { ToastService } from '../../core/toast.service';
import { ActivityDialogComponent } from '../../shared/activity-dialog.component';
import { HasPermissionDirective } from '../../shared/has-permission.directive';
import { MadPipe, ModalComponent, PillComponent, ScoreComponent } from '../../shared/ui';

@Component({
  selector: 'app-lead-detail',
  standalone: true,
  imports: [RouterLink, DatePipe, FormsModule, MadPipe, PillComponent, ScoreComponent, ModalComponent, ActivityDialogComponent, HasPermissionDirective],
  template: `
    <a routerLink="/leads" class="text-sm text-brand-600 hover:underline">← Leads</a>
    @if (lead(); as l) {
      <div class="mt-2 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <div class="flex flex-wrap items-center gap-3">
            <h1>{{ l.fullName }}</h1>
            <app-pill [value]="l.status" [labels]="statusLabels" />
          </div>
          <p class="mt-1 text-sm text-slate-500">{{ l.position ?? '' }}{{ l.companyName ? ' · ' + l.companyName : '' }}</p>
        </div>
        <div class="flex flex-wrap gap-2">
          @if (l.status !== 'CONVERTI') {
            <a [routerLink]="['/leads', l.id, 'edit']" class="btn-secondary">Modifier</a>
            <button class="btn-secondary" (click)="showActivity.set(true)">Planifier</button>
            <button class="btn-primary" (click)="openConvert()">Convertir en client</button>
          } @else {
            @if (l.convertedCompany) { <a [routerLink]="['/companies', l.convertedCompany.id]" class="btn-primary">Voir le client</a> }
          }
          <button *appCan="'DATA_PRIVACY'" class="btn-ghost text-red-600" (click)="anonymize()">Anonymiser</button>
        </div>
      </div>

      <div class="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-3">
        <section class="card p-6">
          <h2>Score IA</h2>
          <div class="mt-3 flex items-end gap-3">
            <span class="text-4xl font-semibold text-slate-900">{{ l.score ?? '—' }}<span class="text-lg text-slate-500"> %</span></span>
            <app-score [value]="l.score" />
          </div>
          <p class="mt-2 text-xs text-slate-500">Probabilité de conversion estimée par le modèle à partir de la source, du profil, du budget et des interactions.
            @if (l.scoredAt) { Calculé le {{ l.scoredAt | date: 'short' }}. }</p>
          <button class="btn-ghost mt-2 px-0 text-brand-600" (click)="rescore()">Recalculer</button>

          <h2 class="mt-6">Informations</h2>
          <dl class="mt-3 space-y-2 text-sm">
            <div><dt class="text-slate-500">E-mail</dt><dd>{{ l.email ?? '—' }}</dd></div>
            <div><dt class="text-slate-500">Téléphone</dt><dd>{{ l.phone ?? '—' }}</dd></div>
            <div><dt class="text-slate-500">Secteur · Ville</dt><dd>{{ l.sector ?? '—' }} · {{ l.city ?? '—' }}</dd></div>
            <div><dt class="text-slate-500">Source</dt><dd>{{ sourceLabels[l.source] }}</dd></div>
            <div><dt class="text-slate-500">Budget estimé</dt><dd>{{ l.estimatedBudget | mad }}</dd></div>
            <div><dt class="text-slate-500">Attribué à</dt><dd>{{ l.assignedTo?.fullName ?? 'Non attribué' }}</dd></div>
            <div><dt class="text-slate-500">Consentement marketing</dt><dd>{{ l.marketingConsent ? 'Oui' : 'Non' }}</dd></div>
            <div><dt class="text-slate-500">Créé le</dt><dd>{{ l.createdAt | date: 'longDate' }}</dd></div>
          </dl>
          @if (l.notes) { <h2 class="mt-6">Notes</h2><p class="mt-2 whitespace-pre-line text-sm">{{ l.notes }}</p> }
        </section>

        <section class="card lg:col-span-2">
          <h2 class="border-b border-slate-200 px-6 py-4">Activités</h2>
          <ul class="divide-y divide-slate-100">
            @for (a of activities(); track a.id) {
              <li class="flex items-start gap-3 px-6 py-3 text-sm">
                <span class="badge bg-slate-100 text-slate-700">{{ typeLabels[a.type] }}</span>
                <div class="flex-1"><p class="font-medium text-slate-900">{{ a.subject }}</p>
                  @if (a.description) { <p class="text-slate-500">{{ a.description }}</p> }</div>
                <span class="whitespace-nowrap text-xs text-slate-400">{{ a.dueAt | date: 'short' }}</span>
              </li>
            } @empty {
              <li class="px-6 py-8 text-center text-sm text-slate-500">Aucune activité pour ce lead.</li>
            }
          </ul>
        </section>
      </div>

      @if (showConvert()) {
        <app-modal title="Convertir en client" [onClose]="closeConvert">
          <div class="space-y-4 text-sm">
            <p class="text-slate-600">Le lead devient un contact rattaché à une entreprise cliente.</p>
            <div>
              <label class="label" for="company">Entreprise</label>
              <select id="company" class="input" [(ngModel)]="convert.existingCompanyId">
                <option [ngValue]="null">Créer « {{ l.companyName || l.fullName }} »</option>
                @for (c of companies(); track c.id) { <option [ngValue]="c.id">{{ c.name }}</option> }
              </select>
            </div>
            <label class="flex items-center gap-2"><input type="checkbox" class="rounded border-slate-300" [(ngModel)]="convert.createOpportunity" /> Créer aussi une opportunité</label>
            @if (convert.createOpportunity) {
              <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div class="sm:col-span-2"><label class="label" for="ot">Titre de l’opportunité</label><input id="ot" class="input" [(ngModel)]="convert.opportunityTitle" /></div>
                <div><label class="label" for="oa">Montant (MAD)</label><input id="oa" type="number" min="0" class="input" [(ngModel)]="convert.amount" /></div>
                <div><label class="label" for="od">Clôture prévue</label><input id="od" type="date" class="input" [(ngModel)]="convert.expectedCloseDate" /></div>
              </div>
            }
            <div class="flex justify-end gap-2">
              <button class="btn-secondary" (click)="closeConvert()">Annuler</button>
              <button class="btn-primary" (click)="doConvert()">Convertir</button>
            </div>
          </div>
        </app-modal>
      }
      @if (showActivity()) {
        <app-activity-dialog [links]="{ leadId: l.id, label: l.fullName }" (closed)="showActivity.set(false)" (saved)="showActivity.set(false); loadActivities()" />
      }
    }
  `
})
export class LeadDetailComponent implements OnInit {
  private api = inject(LeadApi);
  private activityApi = inject(ActivityApi);
  private companyApi = inject(CompanyApi);
  private platform = inject(PlatformApi);
  private router = inject(Router);
  private toast = inject(ToastService);

  id = input.required<string>();
  lead = signal<Lead | null>(null);
  activities = signal<Activity[]>([]);
  companies = signal<Company[]>([]);
  showConvert = signal(false);
  showActivity = signal(false);
  statusLabels = LEAD_STATUS_LABELS;
  sourceLabels = LEAD_SOURCE_LABELS;
  typeLabels = ACTIVITY_TYPE_LABELS;
  convert: { existingCompanyId: number | null; createOpportunity: boolean; opportunityTitle: string; amount: number | null; expectedCloseDate: string | null } =
    { existingCompanyId: null, createOpportunity: true, opportunityTitle: '', amount: null, expectedCloseDate: null };
  closeConvert = () => this.showConvert.set(false);

  ngOnInit(): void {
    this.api.get(+this.id()).subscribe({ next: (l) => this.lead.set(l), error: (err) => { this.toast.error(err); this.router.navigate(['/leads']); } });
    this.loadActivities();
  }

  loadActivities(): void {
    this.activityApi.search({ leadId: this.id(), size: 50, includeSystem: true, ascending: false }).subscribe((p) => this.activities.set(p.content));
  }

  rescore(): void {
    this.api.rescore(+this.id()).subscribe((l) => this.lead.set(l));
  }

  openConvert(): void {
    const l = this.lead()!;
    this.convert.opportunityTitle = `Projet ${l.companyName || l.fullName}`;
    this.convert.amount = l.estimatedBudget;
    this.companyApi.search({ size: 100, sort: 'name' }).subscribe((p) => this.companies.set(p.content));
    this.showConvert.set(true);
  }

  doConvert(): void {
    this.api.convert(+this.id(), this.convert).subscribe({
      next: (r) => {
        this.toast.success('Lead converti en client');
        this.router.navigate(r.opportunityId ? ['/opportunities', r.opportunityId] : ['/companies', r.companyId]);
      },
      error: (err) => this.toast.error(err)
    });
  }

  anonymize(): void {
    if (!confirm('Anonymiser définitivement ce lead (droit d’effacement, loi 09-08) ?')) return;
    this.platform.anonymizeLead(+this.id()).subscribe({
      next: () => {
        this.toast.success('Lead anonymisé');
        this.router.navigate(['/leads']);
      },
      error: (err) => this.toast.error(err)
    });
  }
}
