import { Component, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LossReason } from '../core/models';
import { LOSS_REASON_LABELS } from '../core/sales.models';
import { ModalComponent, entries } from './ui';

/** Motif de perte obligatoire avant de clôturer une opportunité en « Perdue ». */
@Component({
  selector: 'app-loss-dialog',
  standalone: true,
  imports: [FormsModule, ModalComponent],
  template: `
    <app-modal title="Opportunité perdue" [onClose]="cancelFn">
      <p class="-mt-2 mb-4 text-sm text-slate-500">Le motif sert à l’analyse des pertes dans le tableau de bord.</p>
      <div class="space-y-4">
        <div>
          <label class="label" for="reason">Motif *</label>
          <select id="reason" class="input" [(ngModel)]="reason">
            <option [ngValue]="null" disabled>Choisir un motif…</option>
            @for (r of reasons; track r[0]) { <option [ngValue]="r[0]">{{ r[1] }}</option> }
          </select>
        </div>
        <div>
          <label class="label" for="comment">Commentaire</label>
          <textarea id="comment" rows="3" class="input" [(ngModel)]="comment" maxlength="500"></textarea>
        </div>
        <div class="flex justify-end gap-2">
          <button class="btn-secondary" (click)="cancelFn()">Annuler</button>
          <button class="btn-danger" [disabled]="!reason" (click)="confirmed.emit({ lossReason: reason!, lossComment: comment })">
            Marquer perdue
          </button>
        </div>
      </div>
    </app-modal>
  `
})
export class LossDialogComponent {
  reasons = entries(LOSS_REASON_LABELS);
  reason: LossReason | null = null;
  comment = '';
  cancelled = output<void>();
  confirmed = output<{ lossReason: LossReason; lossComment: string }>();
  cancelFn = () => this.cancelled.emit();
}
