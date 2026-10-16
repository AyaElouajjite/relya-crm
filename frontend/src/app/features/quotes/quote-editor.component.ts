import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CompanyApi } from '../../core/api.service';
import { Contact } from '../../core/models';
import { OpportunityApi, ProductApi, QuoteApi } from '../../core/sales.api';
import { Opportunity, Product, QuoteLine } from '../../core/sales.models';
import { ToastService } from '../../core/toast.service';
import { MadPipe, toDateInput } from '../../shared/ui';

/** Éditeur de devis : lignes issues du catalogue ou libres, totaux recalculés en direct. */
@Component({
  selector: 'app-quote-editor',
  standalone: true,
  imports: [FormsModule, RouterLink, MadPipe],
  template: `
    <a [routerLink]="id() ? ['/quotes', id()] : opportunity() ? ['/opportunities', opportunity()!.id] : '/quotes'" class="text-sm text-brand-600 hover:underline">← Retour</a>
    <h1 class="mt-2">{{ id() ? 'Modifier le devis ' + number() : 'Nouveau devis' }}</h1>
    @if (opportunity(); as o) { <p class="text-sm text-slate-500">{{ o.company.label }} · {{ o.title }}</p> }

    <div class="card mt-6 space-y-6 p-6">
      <div class="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <div>
          <label class="label" for="contact">Destinataire</label>
          <select id="contact" class="input" [(ngModel)]="contactId">
            <option [ngValue]="null">—</option>
            @for (c of contacts(); track c.id) { <option [ngValue]="c.id">{{ c.fullName }}{{ c.email ? ' (' + c.email + ')' : '' }}</option> }
          </select>
        </div>
        <div><label class="label" for="date">Date</label><input id="date" type="date" class="input" [(ngModel)]="date" /></div>
        <div><label class="label" for="valid">Valable jusqu’au</label><input id="valid" type="date" class="input" [(ngModel)]="validUntil" /></div>
      </div>

      <div class="overflow-x-auto">
        <table class="table">
          <thead class="bg-slate-50">
            <tr><th class="min-w-[16rem]">Désignation</th><th class="w-24">Qté</th><th class="w-32">PU HT</th><th class="w-24">Remise %</th><th class="w-24">TVA %</th><th class="w-32 text-right">Total HT</th><th class="w-10"></th></tr>
          </thead>
          <tbody>
            @for (l of lines(); track $index; let i = $index) {
              <tr class="hover:bg-transparent">
                <td><input class="input" [(ngModel)]="l.description" (ngModelChange)="touch()" placeholder="Désignation" /></td>
                <td><input class="input" type="number" min="0" step="1" [(ngModel)]="l.quantity" (ngModelChange)="touch()" /></td>
                <td><input class="input" type="number" min="0" step="100" [(ngModel)]="l.unitPrice" (ngModelChange)="touch()" /></td>
                <td><input class="input" type="number" min="0" max="100" [(ngModel)]="l.discount" (ngModelChange)="touch()" /></td>
                <td><input class="input" type="number" min="0" max="100" [(ngModel)]="l.vatRate" (ngModelChange)="touch()" /></td>
                <td class="text-right tabular-nums">{{ lineTotal(l) | mad }}</td>
                <td><button class="btn-ghost text-red-600" (click)="remove(i)" aria-label="Supprimer la ligne">✕</button></td>
              </tr>
            }
          </tbody>
        </table>
      </div>

      <div class="flex flex-col gap-3 sm:flex-row sm:items-end">
        <div class="flex-1">
          <label class="label" for="product">Ajouter depuis le catalogue</label>
          <select id="product" class="input" [ngModel]="null" (ngModelChange)="addProduct($event)">
            <option [ngValue]="null">Choisir un produit ou service…</option>
            @for (p of products(); track p.id) { <option [ngValue]="p">{{ p.name }} — {{ p.priceHT | mad }}</option> }
          </select>
        </div>
        <button class="btn-secondary" (click)="addLine()">+ Ligne libre</button>
      </div>

      <div class="ml-auto w-full max-w-xs space-y-1 text-sm">
        <div class="flex justify-between"><span class="text-slate-500">Total HT</span><span class="tabular-nums">{{ totals().ht | mad }}</span></div>
        <div class="flex justify-between"><span class="text-slate-500">TVA</span><span class="tabular-nums">{{ totals().vat | mad }}</span></div>
        <div class="flex justify-between border-t border-slate-200 pt-1 text-base font-semibold"><span>Total TTC</span><span class="tabular-nums">{{ totals().ht + totals().vat | mad }}</span></div>
      </div>

      <div><label class="label" for="notes">Remarques (imprimées sur le devis)</label><textarea id="notes" rows="3" class="input" [(ngModel)]="notes"></textarea></div>

      <div class="flex justify-end gap-2 border-t border-slate-200 pt-4">
        <button class="btn-primary" [disabled]="!valid() || saving()" (click)="save()">Enregistrer le brouillon</button>
      </div>
    </div>
  `
})
export class QuoteEditorComponent implements OnInit {
  private api = inject(QuoteApi);
  private oppApi = inject(OpportunityApi);
  private productApi = inject(ProductApi);
  private companyApi = inject(CompanyApi);
  private router = inject(Router);
  private toast = inject(ToastService);

  id = input<string>();
  opportunityId = input<string>();
  opportunity = signal<Opportunity | null>(null);
  number = signal('');
  contacts = signal<Contact[]>([]);
  products = signal<Product[]>([]);
  lines = signal<QuoteLine[]>([]);
  saving = signal(false);
  private version = signal(0);
  contactId: number | null = null;
  date = toDateInput(new Date());
  validUntil = '';
  notes = '';

  totals = computed(() => {
    this.version();
    let ht = 0;
    let vat = 0;
    for (const l of this.lines()) {
      const t = this.lineTotal(l);
      ht += t;
      vat += Math.round(t * (Number(l.vatRate) || 0)) / 100;
    }
    return { ht: Math.round(ht * 100) / 100, vat: Math.round(vat * 100) / 100 };
  });

  ngOnInit(): void {
    this.productApi.search({ active: true, size: 200 }).subscribe((p) => this.products.set(p.content));
    if (this.id()) {
      this.api.get(+this.id()!).subscribe((q) => {
        this.number.set(q.number);
        this.contactId = q.contact?.id ?? null;
        this.date = q.date;
        this.validUntil = q.validUntil ?? '';
        this.notes = q.notes ?? '';
        this.lines.set(q.lines.map((l) => ({ ...l })));
        this.loadOpportunity(q.opportunity.id);
      });
    } else if (this.opportunityId()) {
      this.loadOpportunity(+this.opportunityId()!);
      this.addLine();
    } else {
      this.toast.info('Un devis se crée depuis une opportunité');
      this.router.navigate(['/pipeline']);
    }
  }

  private loadOpportunity(id: number): void {
    this.oppApi.get(id).subscribe((o) => {
      this.opportunity.set(o);
      if (!this.id()) this.contactId = o.contact?.id ?? null;
      this.companyApi.get(o.company.id).subscribe((d) => this.contacts.set(d.contacts));
    });
  }

  lineTotal(l: QuoteLine): number {
    const gross = (Number(l.quantity) || 0) * (Number(l.unitPrice) || 0);
    return Math.round(gross * (1 - (Number(l.discount) || 0) / 100) * 100) / 100;
  }

  touch(): void {
    this.version.update((v) => v + 1);
  }

  addLine(): void {
    this.lines.update((l) => [...l, { productId: null, description: '', quantity: 1, unitPrice: 0, discount: 0, vatRate: 20 }]);
  }

  addProduct(p: Product | null): void {
    if (!p) return;
    this.lines.update((l) => [
      ...l.filter((x) => x.description || x.unitPrice),
      { productId: p.id, description: p.name, quantity: 1, unitPrice: p.priceHT, discount: 0, vatRate: p.vatRate }
    ]);
  }

  remove(i: number): void {
    this.lines.update((l) => l.filter((_, idx) => idx !== i));
  }

  valid(): boolean {
    return this.lines().length > 0 && this.lines().every((l) => l.description.trim() && Number(l.quantity) > 0);
  }

  save(): void {
    const body = {
      opportunityId: this.opportunity()!.id,
      contactId: this.contactId,
      date: this.date || null,
      validUntil: this.validUntil || null,
      notes: this.notes,
      lines: this.lines().map((l) => ({ ...l, quantity: Number(l.quantity), unitPrice: Number(l.unitPrice), discount: Number(l.discount) || 0, vatRate: Number(l.vatRate) }))
    };
    this.saving.set(true);
    const id = this.id();
    (id ? this.api.update(+id, body) : this.api.create(body)).subscribe({
      next: (q) => {
        this.toast.success(id ? 'Devis mis à jour' : `Devis ${q.number} créé`);
        this.router.navigate(['/quotes', q.id]);
      },
      error: (err) => {
        this.toast.error(err);
        this.saving.set(false);
      }
    });
  }
}
