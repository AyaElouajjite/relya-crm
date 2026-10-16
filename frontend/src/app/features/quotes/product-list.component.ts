import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ProductType } from '../../core/models';
import { ProductApi } from '../../core/sales.api';
import { Product } from '../../core/sales.models';
import { ToastService } from '../../core/toast.service';
import { HasPermissionDirective } from '../../shared/has-permission.directive';
import { MadPipe, ModalComponent } from '../../shared/ui';

@Component({
  selector: 'app-product-list',
  standalone: true,
  imports: [FormsModule, ReactiveFormsModule, RouterLink, MadPipe, ModalComponent, HasPermissionDirective],
  template: `
    <a routerLink="/quotes" class="text-sm text-brand-600 hover:underline">← Devis</a>
    <div class="mt-2 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
      <div><h1>Catalogue</h1><p class="text-sm text-slate-500">Produits et services proposés dans les devis.</p></div>
      <button *appCan="'PRODUCT_MANAGE'" class="btn-primary" (click)="open(null)">+ Produit / service</button>
    </div>
    <div class="card mt-6 overflow-x-auto">
      <div class="border-b border-slate-200 p-4"><input class="input max-w-sm" placeholder="Rechercher…" [(ngModel)]="q" (ngModelChange)="load()" /></div>
      <table class="table">
        <thead class="bg-slate-50"><tr><th>Désignation</th><th class="hidden sm:table-cell">Référence</th><th>Type</th><th class="text-right">Prix HT</th><th class="text-right">TVA</th><th>État</th><th></th></tr></thead>
        <tbody>
          @for (p of products(); track p.id) {
            <tr>
              <td class="font-medium text-slate-900">{{ p.name }}@if (p.unit) { <span class="text-xs text-slate-400"> / {{ p.unit }}</span> }</td>
              <td class="hidden sm:table-cell">{{ p.reference ?? '—' }}</td>
              <td>{{ p.type === 'SERVICE' ? 'Service' : 'Produit' }}</td>
              <td class="text-right tabular-nums">{{ p.priceHT | mad }}</td>
              <td class="text-right">{{ p.vatRate }} %</td>
              <td><span class="badge" [class]="p.active ? 'bg-emerald-50 text-emerald-800' : 'bg-slate-100 text-slate-500'">{{ p.active ? 'Actif' : 'Inactif' }}</span></td>
              <td class="text-right"><button *appCan="'PRODUCT_MANAGE'" class="btn-ghost" (click)="open(p)">Modifier</button></td>
            </tr>
          }
        </tbody>
      </table>
    </div>

    @if (editing() !== undefined) {
      <app-modal [title]="editing() ? 'Modifier le produit' : 'Nouveau produit / service'" [onClose]="close">
        <form [formGroup]="form" (ngSubmit)="save()" class="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div class="sm:col-span-2"><label class="label" for="n">Désignation *</label><input id="n" class="input" formControlName="name" /></div>
          <div><label class="label" for="r">Référence</label><input id="r" class="input" formControlName="reference" /></div>
          <div><label class="label" for="t">Type</label><select id="t" class="input" formControlName="type"><option value="PRODUIT">Produit</option><option value="SERVICE">Service</option></select></div>
          <div><label class="label" for="p">Prix HT (MAD) *</label><input id="p" type="number" min="0" class="input" formControlName="priceHT" /></div>
          <div><label class="label" for="v">TVA (%)</label><input id="v" type="number" min="0" max="100" class="input" formControlName="vatRate" /></div>
          <div><label class="label" for="u">Unité</label><input id="u" class="input" formControlName="unit" placeholder="jour, unité, an…" /></div>
          <label class="flex items-center gap-2 self-end pb-2 text-sm"><input type="checkbox" class="rounded border-slate-300" formControlName="active" /> Actif</label>
          <div class="sm:col-span-2"><label class="label" for="d">Description</label><textarea id="d" rows="2" class="input" formControlName="description"></textarea></div>
          <div class="flex justify-end gap-2 sm:col-span-2">
            <button type="button" class="btn-secondary" (click)="close()">Annuler</button>
            <button class="btn-primary" [disabled]="form.invalid">Enregistrer</button>
          </div>
        </form>
      </app-modal>
    }
  `
})
export class ProductListComponent implements OnInit {
  private api = inject(ProductApi);
  private toast = inject(ToastService);
  q = '';
  products = signal<Product[]>([]);
  editing = signal<Product | null | undefined>(undefined);
  close = () => this.editing.set(undefined);
  form = inject(FormBuilder).group({
    name: ['', Validators.required],
    reference: [''],
    type: ['SERVICE' as ProductType],
    priceHT: [0, [Validators.required, Validators.min(0)]],
    vatRate: [20, Validators.required],
    unit: [''],
    active: [true],
    description: ['']
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.search({ q: this.q, size: 200 }).subscribe((p) => this.products.set(p.content));
  }

  open(p: Product | null): void {
    this.form.reset({ type: 'SERVICE', priceHT: 0, vatRate: 20, active: true });
    if (p) this.form.patchValue({ ...p, reference: p.reference ?? '', unit: p.unit ?? '', description: p.description ?? '' });
    this.editing.set(p);
  }

  save(): void {
    const p = this.editing();
    (p ? this.api.update(p.id, this.form.getRawValue()) : this.api.create(this.form.getRawValue())).subscribe({
      next: () => {
        this.toast.success('Catalogue mis à jour');
        this.close();
        this.load();
      },
      error: (err) => this.toast.error(err)
    });
  }
}
