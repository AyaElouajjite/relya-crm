import { Component, computed, input, output } from '@angular/core';

@Component({
  selector: 'app-pagination',
  standalone: true,
  template: `
    @if (total() > 0) {
      <div class="flex flex-col items-center justify-between gap-2 border-t border-slate-200 px-4 py-3 text-sm text-slate-600 sm:flex-row">
        <span>{{ from() }}–{{ to() }} sur {{ total() }}</span>
        <div class="flex items-center gap-1">
          <button class="btn-ghost" [disabled]="page() === 0" (click)="pageChange.emit(page() - 1)">‹ Précédent</button>
          <span class="px-2">Page {{ page() + 1 }} / {{ totalPages() }}</span>
          <button class="btn-ghost" [disabled]="page() + 1 >= totalPages()" (click)="pageChange.emit(page() + 1)">Suivant ›</button>
        </div>
      </div>
    }
  `
})
export class PaginationComponent {
  page = input.required<number>();
  size = input.required<number>();
  total = input.required<number>();
  totalPages = input.required<number>();
  pageChange = output<number>();

  from = computed(() => this.page() * this.size() + 1);
  to = computed(() => Math.min((this.page() + 1) * this.size(), this.total()));
}
