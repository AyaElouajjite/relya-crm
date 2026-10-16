import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { BRAND } from '../../core/brand';
import { LogoComponent } from '../../shared/logo.component';

/** Cadre des écrans publics : panneau de marque à gauche (grand écran), formulaire à droite. */
@Component({
  selector: 'app-auth-card',
  standalone: true,
  imports: [LogoComponent, RouterLink],
  template: `
    <div class="flex min-h-screen bg-white">
      <!-- Panneau de marque -->
      <aside class="relative hidden w-[46%] max-w-[640px] flex-col justify-between overflow-hidden bg-brand-950 p-12 text-white lg:flex">
        <div class="pointer-events-none absolute inset-0" aria-hidden="true">
          <div class="absolute -left-24 -top-24 h-96 w-96 rounded-full bg-brand-600/40 blur-3xl"></div>
          <div class="absolute -bottom-32 right-0 h-[28rem] w-[28rem] rounded-full bg-indigo-500/30 blur-3xl"></div>
          <svg class="absolute inset-0 h-full w-full opacity-[0.07]"><defs><pattern id="grid" width="32" height="32" patternUnits="userSpaceOnUse"><path d="M32 0H0v32" fill="none" stroke="#fff" stroke-width="1"/></pattern></defs><rect width="100%" height="100%" fill="url(#grid)"/></svg>
        </div>
        <a routerLink="/" class="relative"><app-logo [size]="40" variant="light" [showProduct]="true" /></a>
        <div class="relative max-w-md">
          <p class="text-3xl font-semibold leading-tight tracking-tight">{{ tagline }}</p>
          <ul class="mt-8 space-y-4 text-sm text-white/75">
            @for (f of features; track f) {
              <li class="flex items-start gap-3">
                <span class="mt-0.5 flex h-5 w-5 flex-none items-center justify-center rounded-full bg-white/10">
                  <svg class="h-3 w-3" viewBox="0 0 12 12" fill="none"><path d="m2.5 6.2 2.2 2.3 4.8-5" stroke="#fff" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>
                </span>
                {{ f }}
              </li>
            }
          </ul>
        </div>
        <p class="relative text-xs text-white/40">© {{ year }} {{ name }} · Données hébergées et protégées conformément à la loi 09-08</p>
      </aside>

      <!-- Formulaire -->
      <main class="flex flex-1 items-center justify-center px-6 py-12 sm:px-12">
        <div class="w-full max-w-[400px] animate-fade-in">
          <div class="mb-10 lg:hidden"><app-logo [size]="36" /></div>
          <h1 class="text-2xl">{{ title() }}</h1>
          @if (subtitle()) { <p class="mt-2 text-sm text-slate-500">{{ subtitle() }}</p> }
          <div class="mt-8"><ng-content /></div>
        </div>
      </main>
    </div>
  `
})
export class AuthCardComponent {
  title = input.required<string>();
  subtitle = input<string>();
  name = BRAND.name;
  tagline = BRAND.tagline;
  year = new Date().getFullYear();
  features = [
    'Leads notés par intelligence artificielle et pipeline visuel',
    'Devis PDF, validation et suivi jusqu’à la facture',
    'Service client et portail dédié pour vos clients',
    'Tableaux de bord et objectifs en temps réel'
  ];
}
