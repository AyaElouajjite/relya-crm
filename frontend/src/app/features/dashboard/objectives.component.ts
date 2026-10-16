import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { DashboardApi } from '../../core/sales.api';
import { ObjectiveRow } from '../../core/sales.models';
import { ToastService } from '../../core/toast.service';

/** Le directeur commercial fixe les objectifs mensuels de CA de chaque commercial. */
@Component({
  selector: 'app-objectives',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div><h1>Objectifs commerciaux</h1><p class="text-sm text-slate-500">Chiffre d’affaires HT visé par mois (MAD).</p></div>
      <div class="flex items-end gap-2">
        <div><label class="label" for="y">Année</label>
          <select id="y" class="input" [(ngModel)]="year" (ngModelChange)="load()">
            @for (y of years; track y) { <option [ngValue]="y">{{ y }}</option> }
          </select></div>
        @if (canEdit) { <button class="btn-primary" (click)="save()" [disabled]="!dirty()">Enregistrer</button> }
      </div>
    </div>
    <div class="card mt-6 overflow-x-auto">
      <table class="table">
        <thead class="bg-slate-50"><tr><th class="sticky left-0 bg-slate-50">Commercial</th>
          @for (m of months; track $index) { <th class="text-right">{{ m }}</th> }<th class="text-right">Total</th></tr></thead>
        <tbody>
          @for (r of rows(); track r.user.id) {
            <tr class="hover:bg-transparent">
              <td class="sticky left-0 bg-white font-medium text-slate-900">{{ r.user.fullName }}</td>
              @for (v of r.months; track $index; let i = $index) {
                <td class="px-1"><input type="number" min="0" step="1000" class="input w-24 px-2 text-right" [disabled]="!canEdit"
                       [ngModel]="v" (ngModelChange)="r.months[i] = $event; dirty.set(true)" /></td>
              }
              <td class="text-right font-medium tabular-nums">{{ total(r).toLocaleString('fr-FR') }}</td>
            </tr>
          }
        </tbody>
      </table>
    </div>
    @if (canEdit) {
      <div class="mt-3 flex gap-2 text-sm">
        <button class="btn-ghost" (click)="fillFromJanuary()">Recopier janvier sur toute l’année</button>
      </div>
    }
  `
})
export class ObjectivesComponent implements OnInit {
  private api = inject(DashboardApi);
  private toast = inject(ToastService);
  canEdit = inject(AuthService).hasPermission('OBJECTIVE_MANAGE');
  months = ['Janv.', 'Févr.', 'Mars', 'Avr.', 'Mai', 'Juin', 'Juil.', 'Août', 'Sept.', 'Oct.', 'Nov.', 'Déc.'];
  year = new Date().getFullYear();
  years = [this.year - 1, this.year, this.year + 1];
  rows = signal<ObjectiveRow[]>([]);
  dirty = signal(false);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.objectives(this.year).subscribe((r) => {
      this.rows.set(r);
      this.dirty.set(false);
    });
  }

  total(r: ObjectiveRow): number {
    return r.months.reduce<number>((s, v) => s + (Number(v) || 0), 0);
  }

  fillFromJanuary(): void {
    this.rows.update((rows) => rows.map((r) => ({ ...r, months: r.months.map(() => r.months[0]) })));
    this.dirty.set(true);
  }

  save(): void {
    const body = this.rows().flatMap((r) =>
      r.months.map((v, i) => ({ userId: r.user.id, year: this.year, month: i + 1, targetAmount: Number(v) || 0 })).filter((x, i) => r.months[i] !== null && r.months[i] !== undefined)
    );
    this.api.saveObjectives(body).subscribe({
      next: (r) => {
        this.rows.set(r);
        this.dirty.set(false);
        this.toast.success('Objectifs enregistrés');
      },
      error: (err) => this.toast.error(err)
    });
  }
}
