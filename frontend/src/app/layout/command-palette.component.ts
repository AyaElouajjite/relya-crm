import { Component, ElementRef, OnInit, inject, input, output, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subject, catchError, debounceTime, forkJoin, map, of, switchMap } from 'rxjs';
import { CompanyApi, ContactApi } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { LeadApi, OpportunityApi } from '../core/sales.api';
import { ICONS } from './icons';

export interface PaletteItem {
  group: string;
  label: string;
  hint?: string;
  icon: string;
  link: string;
}

/** Recherche globale (Ctrl+K) : pages de l'application, entreprises, contacts, leads et opportunités. */
@Component({
  selector: 'app-command-palette',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="fixed inset-0 z-50 flex items-start justify-center bg-slate-900/30 px-4 pt-[12vh] backdrop-blur-[2px]" (click)="closed.emit()">
      <div class="w-full max-w-xl animate-fade-in overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-pop" (click)="$event.stopPropagation()"
           role="dialog" aria-label="Recherche globale">
        <div class="flex items-center gap-3 border-b border-slate-100 px-4">
          <svg class="h-5 w-5 flex-none text-slate-400" fill="none" stroke="currentColor" stroke-width="1.8" viewBox="0 0 24 24"><path [attr.d]="icons['search']" stroke-linecap="round"/></svg>
          <input #field class="h-14 flex-1 border-0 bg-transparent text-[15px] text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-0"
                 placeholder="Rechercher un client, un contact, un lead, une page…" [(ngModel)]="query" (ngModelChange)="search$.next($event)"
                 (keydown)="onKey($event)" autocomplete="off" />
          <kbd class="hidden rounded border border-slate-200 px-1.5 py-0.5 text-[11px] text-slate-400 sm:block">Échap</kbd>
        </div>
        <ul class="max-h-[60vh] overflow-y-auto py-2" role="listbox">
          @for (item of results(); track item.link + item.label; let i = $index) {
            @if (i === 0 || results()[i - 1].group !== item.group) {
              <li class="px-4 pb-1 pt-3 text-[11px] font-semibold uppercase tracking-wider text-slate-400">{{ item.group }}</li>
            }
            <li role="option" [attr.aria-selected]="i === active()">
              <button class="flex w-full items-center gap-3 px-4 py-2 text-left text-sm" [class]="i === active() ? 'bg-brand-50 text-brand-900' : 'text-slate-700'"
                      (mouseenter)="active.set(i)" (click)="go(item)">
                <svg class="h-4 w-4 flex-none" [class]="i === active() ? 'text-brand-600' : 'text-slate-400'" fill="none" stroke="currentColor" stroke-width="1.8" viewBox="0 0 24 24"><path [attr.d]="icons[item.icon]" stroke-linecap="round" stroke-linejoin="round"/></svg>
                <span class="flex-1 truncate">{{ item.label }}</span>
                @if (item.hint) { <span class="truncate text-xs text-slate-400">{{ item.hint }}</span> }
              </button>
            </li>
          } @empty {
            <li class="px-4 py-10 text-center text-sm text-slate-500">{{ loading() ? 'Recherche…' : 'Aucun résultat pour « ' + query + ' »' }}</li>
          }
        </ul>
        <div class="flex items-center gap-4 border-t border-slate-100 bg-slate-50/60 px-4 py-2 text-[11px] text-slate-500">
          <span><kbd class="font-sans">↑ ↓</kbd> naviguer</span><span><kbd class="font-sans">Entrée</kbd> ouvrir</span>
        </div>
      </div>
    </div>
  `
})
export class CommandPaletteComponent implements OnInit {
  private router = inject(Router);
  private auth = inject(AuthService);
  private companies = inject(CompanyApi);
  private contacts = inject(ContactApi);
  private leads = inject(LeadApi);
  private opportunities = inject(OpportunityApi);

  pages = input.required<PaletteItem[]>();
  closed = output<void>();
  field = viewChild.required<ElementRef<HTMLInputElement>>('field');
  icons = ICONS;
  query = '';
  results = signal<PaletteItem[]>([]);
  active = signal(0);
  loading = signal(false);
  search$ = new Subject<string>();

  ngOnInit(): void {
    this.results.set(this.pages());
    setTimeout(() => this.field().nativeElement.focus());
    this.search$
      .pipe(
        debounceTime(200),
        switchMap((q) => {
          const pages = this.matchPages(q);
          if (q.trim().length < 2) return of(pages);
          this.loading.set(true);
          const none = of([] as PaletteItem[]);
          const can = (p: Parameters<AuthService['hasPermission']>[0]) => this.auth.hasPermission(p);
          return forkJoin([
            can('CONTACT_READ') ? this.companies.search({ q, size: 5 }).pipe(map((p) => p.content.map((c) =>
              ({ group: 'Entreprises', label: c.name, hint: c.city ?? '', icon: 'building', link: `/companies/${c.id}` }))), catchError(() => none)) : none,
            can('CONTACT_READ') ? this.contacts.search({ q, size: 5 }).pipe(map((p) => p.content.map((c) =>
              ({ group: 'Contacts', label: c.fullName, hint: c.companyName ?? '', icon: 'users', link: c.companyId ? `/companies/${c.companyId}` : `/contacts/${c.id}/edit` }))), catchError(() => none)) : none,
            can('LEAD_MANAGE') ? this.leads.search({ q, size: 5 }).pipe(map((p) => p.content.map((l) =>
              ({ group: 'Leads', label: l.fullName, hint: l.companyName ?? '', icon: 'target', link: `/leads/${l.id}` }))), catchError(() => none)) : none,
            can('OPPORTUNITY_MANAGE') ? this.opportunities.search({ q, size: 5 }).pipe(map((p) => p.content.map((o) =>
              ({ group: 'Opportunités', label: o.title, hint: o.company.label, icon: 'columns', link: `/opportunities/${o.id}` }))), catchError(() => none)) : none
          ]).pipe(map((groups) => [...pages, ...groups.flat()]));
        })
      )
      .subscribe((r) => {
        this.loading.set(false);
        this.results.set(r);
        this.active.set(0);
      });
  }

  private matchPages(q: string): PaletteItem[] {
    const norm = (s: string) => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
    return this.pages().filter((p) => norm(p.label).includes(norm(q.trim())));
  }

  onKey(e: KeyboardEvent): void {
    const n = this.results().length;
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      this.active.set(n ? (this.active() + 1) % n : 0);
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      this.active.set(n ? (this.active() - 1 + n) % n : 0);
    } else if (e.key === 'Enter' && n) {
      this.go(this.results()[this.active()]);
    } else if (e.key === 'Escape') {
      this.closed.emit();
    }
  }

  go(item: PaletteItem): void {
    this.closed.emit();
    this.router.navigateByUrl(item.link);
  }
}
