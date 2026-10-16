import { Component, OnInit, inject, signal } from '@angular/core';
import { UserApi } from '../../core/api.service';
import { Permission, Role } from '../../core/models';
import { PlatformApi } from '../../core/sales.api';
import { ToastService } from '../../core/toast.service';

const GROUPS: { title: string; items: [Permission, string][] }[] = [
  { title: 'Clients', items: [['CONTACT_READ', 'Consulter entreprises et contacts'], ['CONTACT_WRITE', 'Créer / modifier'], ['CONTACT_DELETE', 'Supprimer']] },
  { title: 'Ventes', items: [['LEAD_MANAGE', 'Gérer les leads'], ['LEAD_ASSIGN', 'Attribuer les leads'], ['OPPORTUNITY_MANAGE', 'Gérer le pipeline'],
    ['QUOTE_MANAGE', 'Créer et envoyer des devis'], ['QUOTE_APPROVE', 'Valider les devis importants'], ['PRODUCT_MANAGE', 'Gérer le catalogue'],
    ['ACTIVITY_MANAGE', 'Activités et agenda'], ['ALL_RECORDS', 'Modifier le portefeuille des autres']] },
  { title: 'Pilotage', items: [['DASHBOARD_VIEW', 'Tableau de bord personnel'], ['DASHBOARD_ALL', 'Tableaux de bord de l’équipe'], ['OBJECTIVE_MANAGE', 'Fixer les objectifs']] },
  { title: 'Support', items: [['TICKET_MANAGE', 'Traiter les tickets SAV'], ['PORTAL_ACCESS', 'Accès au portail client']] },
  { title: 'Administration', items: [['USER_MANAGE', 'Utilisateurs et rôles'], ['SETTINGS_MANAGE', 'Paramètres'], ['AUDIT_READ', 'Journal d’audit'],
    ['DATA_PRIVACY', 'Droits des personnes (loi 09-08)'], ['DATA_EXPORT', 'Exporter'], ['DATA_IMPORT', 'Importer']] }
];

/** Matrice rôles × permissions (RBAC), modifiable par l'administrateur. */
@Component({
  selector: 'app-roles',
  standalone: true,
  template: `
    <section class="card mt-8 overflow-x-auto">
      <div class="flex flex-col gap-2 border-b border-slate-200 px-6 py-4 sm:flex-row sm:items-center sm:justify-between">
        <div><h2>Rôles et permissions</h2><p class="text-xs text-slate-500">Les changements s’appliquent dès la prochaine requête des utilisateurs concernés.</p></div>
        <button class="btn-primary" [disabled]="!dirty()" (click)="save()">Enregistrer les rôles</button>
      </div>
      <table class="table">
        <thead class="bg-slate-50">
          <tr><th>Permission</th>@for (r of roles(); track r.id) { <th class="text-center">{{ r.label }}</th> }</tr>
        </thead>
        <tbody>
          @for (g of groups; track g.title) {
            <tr class="bg-slate-50/60 hover:bg-slate-50/60"><td [attr.colspan]="roles().length + 1" class="py-2 text-xs font-semibold uppercase tracking-wide text-slate-500">{{ g.title }}</td></tr>
            @for (p of g.items; track p[0]) {
              <tr>
                <td>{{ p[1] }}</td>
                @for (r of roles(); track r.id) {
                  <td class="text-center">
                    <input type="checkbox" class="rounded border-slate-300" [checked]="r.permissions.includes(p[0])" (change)="toggle(r, p[0])"
                           [attr.aria-label]="r.label + ' : ' + p[1]" />
                  </td>
                }
              </tr>
            }
          }
        </tbody>
      </table>
    </section>
  `
})
export class RolesComponent implements OnInit {
  private users = inject(UserApi);
  private platform = inject(PlatformApi);
  private toast = inject(ToastService);
  groups = GROUPS;
  roles = signal<Role[]>([]);
  dirty = signal(false);
  private changed = new Set<number>();

  ngOnInit(): void {
    this.users.roles().subscribe((r) => this.roles.set(r));
  }

  toggle(role: Role, p: Permission): void {
    this.roles.update((list) => list.map((r) => r.id !== role.id ? r
      : { ...r, permissions: r.permissions.includes(p) ? r.permissions.filter((x) => x !== p) : [...r.permissions, p] }));
    this.changed.add(role.id);
    this.dirty.set(true);
  }

  save(): void {
    const toSave = this.roles().filter((r) => this.changed.has(r.id));
    let pending = toSave.length;
    for (const r of toSave) {
      this.platform.updateRole(r.id, r.permissions).subscribe({
        next: () => {
          if (--pending === 0) {
            this.changed.clear();
            this.dirty.set(false);
            this.toast.success('Rôles enregistrés');
          }
        },
        error: (err) => this.toast.error(err)
      });
    }
  }
}
