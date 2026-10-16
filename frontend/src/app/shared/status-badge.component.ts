import { Component, computed, input } from '@angular/core';
import { COMPANY_STATUS_LABELS, CompanyStatus } from '../core/models';

@Component({
  selector: 'app-status-badge',
  standalone: true,
  template: `<span class="badge" [class]="classes()">{{ label() }}</span>`
})
export class StatusBadgeComponent {
  status = input.required<CompanyStatus>();
  label = computed(() => COMPANY_STATUS_LABELS[this.status()]);
  classes = computed(
    () =>
      ({
        CLIENT: 'bg-emerald-50 text-emerald-700',
        PROSPECT: 'bg-amber-50 text-amber-700',
        INACTIF: 'bg-slate-100 text-slate-600'
      })[this.status()]
  );
}
