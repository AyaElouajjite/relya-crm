import { Component, model } from '@angular/core';

/** Saisie de tags : Entrée ou virgule pour ajouter, × pour retirer. Usage : <app-tag-input [(tags)]="tags" /> */
@Component({
  selector: 'app-tag-input',
  standalone: true,
  template: `
    <div class="input flex min-h-[42px] flex-wrap items-center gap-1.5 py-1.5" (click)="field.focus()">
      @for (tag of tags(); track tag) {
        <span class="badge gap-1 bg-brand-50 text-brand-700">
          {{ tag }}
          <button type="button" class="text-brand-400 hover:text-brand-700" (click)="remove(tag)" [attr.aria-label]="'Retirer ' + tag">×</button>
        </span>
      }
      <input #field class="min-w-[8rem] flex-1 border-0 p-0 text-sm focus:outline-none focus:ring-0"
             placeholder="Ajouter un tag…" (keydown)="onKey($event, field)" (blur)="add(field)" />
    </div>
  `
})
export class TagInputComponent {
  tags = model<string[]>([]);

  onKey(event: KeyboardEvent, field: HTMLInputElement): void {
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault();
      this.add(field);
    } else if (event.key === 'Backspace' && !field.value && this.tags().length) {
      this.tags.update((t) => t.slice(0, -1));
    }
  }

  add(field: HTMLInputElement): void {
    const value = field.value.trim().replace(/,$/, '');
    if (value && !this.tags().includes(value)) {
      this.tags.update((t) => [...t, value]);
    }
    field.value = '';
  }

  remove(tag: string): void {
    this.tags.update((t) => t.filter((x) => x !== tag));
  }
}
