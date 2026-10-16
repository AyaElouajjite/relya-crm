import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { UserApi } from '../core/api.service';
import { ActivityType, UserSummary } from '../core/models';
import { ActivityApi } from '../core/sales.api';
import { ACTIVITY_TYPE_LABELS, Activity } from '../core/sales.models';
import { ToastService } from '../core/toast.service';
import { ModalComponent, toDateTimeInput } from './ui';

export interface ActivityLinks {
  companyId?: number | null;
  contactId?: number | null;
  opportunityId?: number | null;
  leadId?: number | null;
  label?: string;
}

/** Planifier ou modifier un appel, un rendez-vous, une tâche... lié à un client. */
@Component({
  selector: 'app-activity-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, ModalComponent],
  template: `
    <app-modal [title]="activity() ? 'Modifier l’activité' : 'Planifier une activité'" [onClose]="closeFn">
      @if (links()?.label) { <p class="-mt-2 mb-4 text-sm text-slate-500">Liée à : {{ links()!.label }}</p> }
      <form [formGroup]="form" (ngSubmit)="save()" class="space-y-4">
        <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <label class="label" for="type">Type</label>
            <select id="type" class="input" formControlName="type">
              @for (t of types; track t[0]) { <option [value]="t[0]">{{ t[1] }}</option> }
            </select>
          </div>
          <div>
            <label class="label" for="owner">Responsable</label>
            <select id="owner" class="input" formControlName="ownerId">
              <option [ngValue]="null">— Moi —</option>
              @for (u of owners(); track u.id) { <option [ngValue]="u.id">{{ u.fullName }}</option> }
            </select>
          </div>
          <div class="sm:col-span-2">
            <label class="label" for="subject">Sujet *</label>
            <input id="subject" class="input" formControlName="subject" />
          </div>
          <div>
            <label class="label" for="due">Date et heure *</label>
            <input id="due" type="datetime-local" class="input" formControlName="dueAt" />
          </div>
          <div>
            <label class="label" for="duration">Durée (min)</label>
            <input id="duration" type="number" min="0" step="15" class="input" formControlName="durationMinutes" />
          </div>
          <div class="sm:col-span-2">
            <label class="label" for="reminder">Rappel</label>
            <select id="reminder" class="input" formControlName="reminderMinutesBefore">
              <option [ngValue]="null">Aucun</option>
              <option [ngValue]="0">À l’heure prévue</option>
              <option [ngValue]="15">15 minutes avant</option>
              <option [ngValue]="60">1 heure avant</option>
              <option [ngValue]="1440">La veille</option>
            </select>
          </div>
          <div class="sm:col-span-2">
            <label class="label" for="desc">Description</label>
            <textarea id="desc" rows="3" class="input" formControlName="description"></textarea>
          </div>
        </div>
        <div class="flex justify-end gap-2">
          <button type="button" class="btn-secondary" (click)="closeFn()">Annuler</button>
          <button type="submit" class="btn-primary" [disabled]="form.invalid || saving()">Enregistrer</button>
        </div>
      </form>
    </app-modal>
  `
})
export class ActivityDialogComponent implements OnInit {
  private api = inject(ActivityApi);
  private users = inject(UserApi);
  private toast = inject(ToastService);

  activity = input<Activity | null>(null);
  links = input<ActivityLinks | null>(null);
  defaultType = input<ActivityType>('APPEL');
  closed = output<void>();
  saved = output<Activity>();
  closeFn = () => this.closed.emit();

  types = Object.entries(ACTIVITY_TYPE_LABELS).filter(([k]) => k !== 'SYSTEME') as [ActivityType, string][];
  owners = signal<UserSummary[]>([]);
  saving = signal(false);

  form = inject(FormBuilder).group({
    type: ['APPEL' as ActivityType, Validators.required],
    subject: ['', [Validators.required, Validators.maxLength(200)]],
    dueAt: ['', Validators.required],
    durationMinutes: [30 as number | null],
    reminderMinutesBefore: [15 as number | null],
    description: [''],
    ownerId: [null as number | null]
  });

  ngOnInit(): void {
    this.users.assignable().subscribe((u) => this.owners.set(u));
    const a = this.activity();
    if (a) {
      const reminder = a.reminderAt ? Math.round((new Date(a.dueAt).getTime() - new Date(a.reminderAt).getTime()) / 60000) : null;
      this.form.patchValue({
        type: a.type,
        subject: a.subject,
        dueAt: toDateTimeInput(a.dueAt),
        durationMinutes: a.durationMinutes,
        reminderMinutesBefore: reminder,
        description: a.description ?? '',
        ownerId: a.owner?.id ?? null
      });
    } else {
      const next = new Date();
      next.setDate(next.getDate() + 1);
      next.setHours(10, 0, 0, 0);
      this.form.patchValue({ type: this.defaultType(), dueAt: toDateTimeInput(next) });
    }
  }

  save(): void {
    const v = this.form.getRawValue();
    const a = this.activity();
    const l = this.links() ?? {};
    const body = {
      ...v,
      dueAt: new Date(v.dueAt!).toISOString(),
      companyId: a?.company?.id ?? l.companyId ?? null,
      contactId: a?.contact?.id ?? l.contactId ?? null,
      opportunityId: a?.opportunity?.id ?? l.opportunityId ?? null,
      leadId: a?.lead?.id ?? l.leadId ?? null,
      status: a?.status
    };
    this.saving.set(true);
    (a ? this.api.update(a.id, body) : this.api.create(body)).subscribe({
      next: (res) => {
        this.toast.success(a ? 'Activité mise à jour' : 'Activité planifiée');
        this.saved.emit(res);
      },
      error: (err) => {
        this.toast.error(err);
        this.saving.set(false);
      }
    });
  }
}
