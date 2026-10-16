import { Component, input, output, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { ImportResult } from '../core/models';
import { errorMessage } from '../core/toast.service';

/** Fenêtre d'import CSV commune aux entreprises et aux contacts. */
@Component({
  selector: 'app-import-dialog',
  standalone: true,
  template: `
    <div class="fixed inset-0 z-40 flex items-center justify-center bg-slate-900/40 p-4" (click)="close.emit()">
      <div class="card w-full max-w-lg p-6" (click)="$event.stopPropagation()">
        <h2>{{ title() }}</h2>
        <p class="mt-1 text-sm text-slate-500">
          Fichier CSV (séparateur « ; » ou « , »), première ligne = en-têtes.
          Colonnes reconnues : <code class="text-xs">{{ columns() }}</code>
        </p>
        <input type="file" accept=".csv,text/csv" class="mt-4 block w-full text-sm" (change)="onFile($event)" />

        @if (result(); as r) {
          <div class="mt-4 rounded-lg bg-slate-50 p-3 text-sm">
            <p class="font-medium text-emerald-700">{{ r.created }} créé(s), {{ r.updated }} mis à jour.</p>
            @if (r.errors.length) {
              <p class="mt-2 font-medium text-amber-700">{{ r.errors.length }} ligne(s) ignorée(s) :</p>
              <ul class="mt-1 max-h-32 list-disc overflow-auto pl-5 text-amber-700">
                @for (e of r.errors; track $index) { <li>{{ e }}</li> }
              </ul>
            }
          </div>
        }
        @if (error()) { <p class="field-error mt-3">{{ error() }}</p> }

        <div class="mt-6 flex justify-end gap-2">
          <button class="btn-secondary" (click)="close.emit()">Fermer</button>
          <button class="btn-primary" [disabled]="!file() || loading()" (click)="run()">
            {{ loading() ? 'Import…' : 'Importer' }}
          </button>
        </div>
      </div>
    </div>
  `
})
export class ImportDialogComponent {
  title = input('Importer');
  columns = input('');
  upload = input.required<(file: File) => Observable<ImportResult>>();
  close = output<void>();
  imported = output<ImportResult>();

  file = signal<File | null>(null);
  loading = signal(false);
  result = signal<ImportResult | null>(null);
  error = signal<string | null>(null);

  onFile(event: Event): void {
    this.file.set((event.target as HTMLInputElement).files?.[0] ?? null);
    this.result.set(null);
    this.error.set(null);
  }

  run(): void {
    const file = this.file();
    if (!file) return;
    this.loading.set(true);
    this.upload()(file).subscribe({
      next: (r) => {
        this.result.set(r);
        this.loading.set(false);
        this.imported.emit(r);
      },
      error: (err) => {
        this.error.set(errorMessage(err, "L'import a échoué"));
        this.loading.set(false);
      }
    });
  }
}
