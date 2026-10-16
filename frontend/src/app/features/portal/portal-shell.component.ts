import { Component, OnInit, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { RealtimeService } from '../../core/realtime.service';
import { LogoComponent } from '../../shared/logo.component';

/** Espace client (portail) : mise en page séparée de l'application interne. */
@Component({
  selector: 'app-portal-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, LogoComponent],
  template: `
    <div class="min-h-screen bg-slate-50">
      <header class="border-b border-slate-200 bg-white">
        <div class="mx-auto flex max-w-5xl flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3">
          <span class="flex items-center gap-3"><app-logo [size]="30" /><span class="hidden border-l border-slate-200 pl-3 text-sm font-medium text-slate-500 sm:inline">Espace client</span></span>
          <nav class="order-3 flex w-full gap-1 overflow-x-auto text-sm sm:order-none sm:w-auto">
            @for (l of links; track l.path) {
              <a [routerLink]="l.path" routerLinkActive="bg-brand-50 text-brand-700" [routerLinkActiveOptions]="{ exact: l.exact }"
                 class="whitespace-nowrap rounded-lg px-3 py-1.5 text-slate-600 hover:bg-slate-100">{{ l.label }}</a>
            }
          </nav>
          <div class="ml-auto flex items-center gap-3 text-sm">
            @if (realtime.unread()) { <span class="badge bg-red-600 text-white" title="Notifications non lues">{{ realtime.unread() }}</span> }
            <span class="hidden text-slate-600 sm:inline">{{ auth.user()?.fullName }}</span>
            <button class="btn-ghost text-red-600" (click)="signOut()">Déconnexion</button>
          </div>
        </div>
      </header>
      <main class="mx-auto max-w-5xl px-4 py-6"><router-outlet /></main>
      <footer class="mx-auto max-w-5xl px-4 pb-8 text-xs text-slate-400">
        <a routerLink="/confidentialite" class="hover:underline">Protection des données personnelles (loi 09-08)</a>
      </footer>
    </div>
  `
})
export class PortalShellComponent implements OnInit {
  auth = inject(AuthService);
  realtime = inject(RealtimeService);
  links = [
    { path: '/portal', label: 'Accueil', exact: true },
    { path: '/portal/quotes', label: 'Mes devis', exact: false },
    { path: '/portal/invoices', label: 'Mes factures', exact: false },
    { path: '/portal/tickets', label: 'Mes demandes', exact: false }
  ];

  ngOnInit(): void {
    this.realtime.start();
  }

  signOut(): void {
    this.realtime.stop();
    this.auth.signOut();
  }
}
