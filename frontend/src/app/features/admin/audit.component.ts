import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { UserApi } from '../../core/api.service';
import { AuditLog, LoginLog, Page } from '../../core/models';
import { PaginationComponent } from '../../shared/pagination.component';

const ACTION_LABELS: Record<string, string> = {
  CREATE: 'Création',
  UPDATE: 'Modification',
  DELETE: 'Suppression',
  ARCHIVE: 'Archivage',
  RESTORE: 'Restauration',
  EXPORT: 'Export',
  IMPORT: 'Import'
};

@Component({
  selector: 'app-audit',
  standalone: true,
  imports: [DatePipe, PaginationComponent],
  template: `
    <h1>Journal</h1>
    <p class="text-sm text-slate-500">Traçabilité des actions sensibles et des connexions.</p>

    <div class="mt-6 flex gap-2">
      <button [class]="tab() === 'audit' ? 'btn-primary' : 'btn-secondary'" (click)="tab.set('audit')">Actions</button>
      <button [class]="tab() === 'logins' ? 'btn-primary' : 'btn-secondary'" (click)="tab.set('logins')">Connexions</button>
    </div>

    <div class="card mt-4 overflow-x-auto">
      @if (tab() === 'audit') {
        <table class="table">
          <thead class="bg-slate-50"><tr><th>Date</th><th>Utilisateur</th><th>Action</th><th>Objet</th><th class="hidden md:table-cell">Détails</th></tr></thead>
          <tbody>
            @for (a of audit()?.content; track a.id) {
              <tr>
                <td class="whitespace-nowrap">{{ a.at | date: 'short' }}</td>
                <td>{{ a.actor }}</td>
                <td>{{ actionLabel(a.action) }}</td>
                <td>{{ a.entityType }}@if (a.entityId) { #{{ a.entityId }} }</td>
                <td class="hidden md:table-cell">{{ a.details }}</td>
              </tr>
            }
          </tbody>
        </table>
        @if (audit(); as p) {
          <app-pagination [page]="p.page" [size]="p.size" [total]="p.totalElements" [totalPages]="p.totalPages" (pageChange)="loadAudit($event)" />
        }
      } @else {
        <table class="table">
          <thead class="bg-slate-50"><tr><th>Date</th><th>Email</th><th>Résultat</th><th class="hidden sm:table-cell">IP</th></tr></thead>
          <tbody>
            @for (l of logins()?.content; track l.id) {
              <tr>
                <td class="whitespace-nowrap">{{ l.at | date: 'short' }}</td>
                <td>{{ l.email }}</td>
                <td><span class="badge" [class]="l.success ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-700'">{{ l.success ? 'Réussie' : 'Échec' }}</span></td>
                <td class="hidden sm:table-cell">{{ l.ipAddress }}</td>
              </tr>
            }
          </tbody>
        </table>
        @if (logins(); as p) {
          <app-pagination [page]="p.page" [size]="p.size" [total]="p.totalElements" [totalPages]="p.totalPages" (pageChange)="loadLogins($event)" />
        }
      }
    </div>
  `
})
export class AuditComponent implements OnInit {
  private api = inject(UserApi);
  tab = signal<'audit' | 'logins'>('audit');
  audit = signal<Page<AuditLog> | null>(null);
  logins = signal<Page<LoginLog> | null>(null);

  ngOnInit(): void {
    this.loadAudit(0);
    this.loadLogins(0);
  }

  loadAudit(page: number): void {
    this.api.audit(page).subscribe((p) => this.audit.set(p));
  }

  loadLogins(page: number): void {
    this.api.allLogins({ page }).subscribe((p) => this.logins.set(p));
  }

  actionLabel(action: string): string {
    return ACTION_LABELS[action] ?? action;
  }
}
