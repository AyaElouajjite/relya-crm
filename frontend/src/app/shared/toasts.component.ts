import { Component, inject } from '@angular/core';
import { ToastService } from '../core/toast.service';

@Component({
  selector: 'app-toasts',
  standalone: true,
  template: `
    <div class="pointer-events-none fixed bottom-4 right-4 z-50 flex w-full max-w-sm flex-col gap-2 px-4 sm:px-0" aria-live="polite">
      @for (t of toasts.toasts(); track t.id) {
        <div class="pointer-events-auto flex items-start gap-3 rounded-lg px-4 py-3 text-sm shadow-lg"
             [class]="t.type === 'error' ? 'bg-red-600 text-white' : t.type === 'success' ? 'bg-emerald-600 text-white' : 'bg-slate-800 text-white'">
          <span class="flex-1">{{ t.message }}</span>
          <button class="opacity-70 hover:opacity-100" (click)="toasts.dismiss(t.id)" aria-label="Fermer">×</button>
        </div>
      }
    </div>
  `
})
export class ToastsComponent {
  toasts = inject(ToastService);
}
