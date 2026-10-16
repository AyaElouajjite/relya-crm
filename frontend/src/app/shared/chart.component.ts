import { AfterViewInit, Component, ElementRef, OnDestroy, effect, input, viewChild } from '@angular/core';
import {
  BarController,
  BarElement,
  CategoryScale,
  Chart,
  ChartConfiguration,
  Legend,
  LineController,
  LineElement,
  LinearScale,
  PointElement,
  Tooltip
} from 'chart.js';

Chart.register(BarController, BarElement, LineController, LineElement, PointElement, CategoryScale, LinearScale, Tooltip, Legend);

/** Palette catégorielle validée (ordre fixe, jamais cyclée) — voir docs/dataviz. */
export const SERIES = ['#2a78d6', '#eb6834', '#1baf7a', '#eda100'];
export const INK = { primary: '#0f172a', secondary: '#475569', muted: '#94a3b8', grid: '#e2e8f0', surface: '#ffffff' };

Chart.defaults.font.family = 'Inter, system-ui, sans-serif';
Chart.defaults.font.size = 12;
Chart.defaults.color = INK.secondary;

/** Enveloppe Chart.js : recrée le graphique quand la configuration change. */
@Component({
  selector: 'app-chart',
  standalone: true,
  template: `<div class="relative w-full" [style.height.px]="height()"><canvas #canvas [attr.aria-label]="label()" role="img"></canvas></div>`
})
export class ChartComponent implements AfterViewInit, OnDestroy {
  config = input.required<ChartConfiguration>();
  height = input(260);
  label = input('Graphique');
  private canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private chart: Chart | null = null;
  private ready = false;

  constructor() {
    effect(() => {
      const cfg = this.config();
      if (this.ready) this.render(cfg);
    });
  }

  ngAfterViewInit(): void {
    this.ready = true;
    this.render(this.config());
  }

  private render(cfg: ChartConfiguration): void {
    this.chart?.destroy();
    this.chart = new Chart(this.canvas().nativeElement, cfg);
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
  }
}

const compact = new Intl.NumberFormat('fr-FR', { notation: 'compact', maximumFractionDigits: 1 });
const full = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 });

/** Barres horizontales à une seule série (pas de légende : le titre de la carte nomme la série). */
export function horizontalBars(labels: string[], values: number[], money = false): ChartConfiguration {
  return {
    type: 'bar',
    data: {
      labels,
      datasets: [{ data: values, backgroundColor: SERIES[0], borderRadius: 4, borderSkipped: 'start', barThickness: 18, maxBarThickness: 22 }]
    },
    options: {
      indexAxis: 'y',
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: { callbacks: { label: (c) => (money ? `${full.format(c.parsed.x)} MAD` : `${c.parsed.x}`) } }
      },
      scales: {
        x: { beginAtZero: true, grid: { color: INK.grid }, border: { display: false }, ticks: { precision: 0, callback: (v) => compact.format(Number(v)) } },
        y: { grid: { display: false }, border: { display: false }, ticks: { color: INK.primary } }
      }
    }
  };
}

/** CA réalisé (barres) et objectif (ligne) sur le même axe en MAD. */
export function revenueVsObjective(labels: string[], revenue: number[], objective: number[]): ChartConfiguration {
  return {
    type: 'bar',
    data: {
      labels,
      datasets: [
        { type: 'bar', label: 'CA réalisé HT', data: revenue, backgroundColor: SERIES[0], borderRadius: 4, borderSkipped: 'start', maxBarThickness: 28, order: 2 },
        {
          type: 'line', label: 'Objectif', data: objective, borderColor: SERIES[1], backgroundColor: SERIES[1], borderWidth: 2,
          pointRadius: 4, pointHoverRadius: 6, pointBorderColor: INK.surface, pointBorderWidth: 2, tension: 0, order: 1
        }
      ]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: { position: 'top', align: 'end', labels: { usePointStyle: true, boxWidth: 8, color: INK.secondary, sort: (a, b) => (a.datasetIndex ?? 0) - (b.datasetIndex ?? 0) } },
        tooltip: { callbacks: { label: (c) => `${c.dataset.label} : ${full.format(c.parsed.y)} MAD` } }
      },
      scales: {
        y: { beginAtZero: true, grid: { color: INK.grid }, border: { display: false }, ticks: { callback: (v) => compact.format(Number(v)) } },
        x: { grid: { display: false }, border: { display: false } }
      }
    }
  };
}
