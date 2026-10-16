import { Component, Pipe, PipeTransform, computed, input } from '@angular/core';
import { BADGE } from '../core/sales.models';

/** Montant en dirhams : 12 500,00 MAD */
@Pipe({ name: 'mad', standalone: true })
export class MadPipe implements PipeTransform {
  private static readonly fmt = new Intl.NumberFormat('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  private static readonly compact = new Intl.NumberFormat('fr-FR', { notation: 'compact', maximumFractionDigits: 1 });

  transform(value: number | null | undefined, compact = false): string {
    if (value === null || value === undefined) return '—';
    return (compact ? MadPipe.compact.format(value) : MadPipe.fmt.format(value)) + ' MAD';
  }
}

/** Pastille de statut : couleur ET libellé (jamais la couleur seule). */
@Component({
  selector: 'app-pill',
  standalone: true,
  template: `<span class="badge whitespace-nowrap" [class]="classes()">{{ labelOf() }}</span>`
})
export class PillComponent {
  value = input.required<string>();
  labels = input.required<Record<string, string>>();
  classes = computed(() => BADGE[this.value()] ?? 'bg-slate-100 text-slate-700');
  labelOf = computed(() => (this.labels() as Record<string, string | undefined>)[this.value()] ?? this.value());
}

/** Score IA d'un lead : jauge + pourcentage. */
@Component({
  selector: 'app-score',
  standalone: true,
  template: `
    @if (value() !== null && value() !== undefined) {
      <span class="inline-flex items-center gap-2 whitespace-nowrap" [title]="'Probabilité de conversion estimée : ' + value() + ' %'">
        <span class="h-1.5 w-12 overflow-hidden rounded-full bg-slate-200">
          <span class="block h-full rounded-full" [class]="bar()" [style.width.%]="value()"></span>
        </span>
        <span class="text-xs font-medium tabular-nums text-slate-700">{{ value() }} %</span>
      </span>
    } @else {
      <span class="text-xs text-slate-400">—</span>
    }
  `
})
export class ScoreComponent {
  value = input<number | null>();
  bar = computed(() => {
    const v = this.value() ?? 0;
    return v >= 60 ? 'bg-emerald-500' : v >= 35 ? 'bg-amber-500' : 'bg-slate-400';
  });
}

/** Fenêtre modale simple. */
@Component({
  selector: 'app-modal',
  standalone: true,
  template: `
    <div class="fixed inset-0 z-40 flex items-start justify-center overflow-y-auto bg-slate-900/40 p-4 sm:items-center" (click)="close()">
      <div class="card my-8 w-full p-6" [class]="width()" (click)="$event.stopPropagation()" role="dialog" aria-modal="true">
        <div class="mb-4 flex items-start justify-between gap-4">
          <h2>{{ title() }}</h2>
          <button class="btn-ghost -mr-2 -mt-1" (click)="close()" aria-label="Fermer">✕</button>
        </div>
        <ng-content />
      </div>
    </div>
  `
})
export class ModalComponent {
  title = input.required<string>();
  width = input('max-w-lg');
  onClose = input<() => void>(() => {});
  close(): void {
    this.onClose()();
  }
}

export function toDateInput(value: string | Date | null | undefined): string {
  if (!value) return '';
  const d = typeof value === 'string' ? new Date(value) : value;
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function toDateTimeInput(value: string | Date | null | undefined): string {
  if (!value) return '';
  const d = typeof value === 'string' ? new Date(value) : value;
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${toDateInput(d)}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export function entries<T extends string>(labels: Record<T, string>): [T, string][] {
  return Object.entries(labels) as [T, string][];
}
