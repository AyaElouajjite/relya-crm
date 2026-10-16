import { DatePipe } from '@angular/common';
import { Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRouteSnapshot, NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { Permission } from '../core/models';
import { RealtimeService } from '../core/realtime.service';
import { AppNotification } from '../core/sales.models';
import { LogoComponent } from '../shared/logo.component';
import { CommandPaletteComponent, PaletteItem } from './command-palette.component';
import { ICONS } from './icons';

interface NavItem {
  label: string;
  path: string;
  icon: string;
  permission?: Permission | Permission[];
  /** Masqué si l'utilisateur a déjà cette permission (évite les doublons de menu). */
  onlyWithout?: Permission;
}

interface QuickAction {
  label: string;
  path: string;
  icon: string;
  permission: Permission;
}

const COLLAPSE_KEY = 'crm.sidebar.collapsed';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, DatePipe, LogoComponent, CommandPaletteComponent],
  template: `
    <div class="flex min-h-screen">
      @if (menuOpen()) {
        <div class="fixed inset-0 z-30 bg-slate-900/30 backdrop-blur-[1px] lg:hidden" (click)="menuOpen.set(false)"></div>
      }

      <!-- Barre latérale -->
      <aside class="fixed inset-y-0 left-0 z-40 flex flex-col border-r border-slate-200 bg-white transition-all duration-200 lg:sticky lg:top-0 lg:h-screen lg:translate-x-0"
             [class]="(collapsed() ? 'lg:w-[72px] ' : 'lg:w-64 ') + (menuOpen() ? 'w-72 translate-x-0' : 'w-72 -translate-x-full')">
        <div class="flex h-16 flex-none items-center px-5" [class.lg:justify-center]="collapsed()" [class.lg:px-0]="collapsed()">
          <a routerLink="/dashboard" aria-label="Accueil">
            <span class="lg:hidden"><app-logo [size]="32" /></span>
            <span class="hidden lg:inline"><app-logo [size]="32" [showText]="!collapsed()" /></span>
          </a>
        </div>

        <nav class="flex-1 overflow-y-auto px-3 pb-4 pt-2" [class]="collapsed() ? 'lg:space-y-3 space-y-6' : 'space-y-6'">
          @for (group of navGroups(); track group.title) {
            <div>
              <p class="px-3 pb-1.5 text-[11px] font-semibold uppercase tracking-wider text-slate-400" [class.lg:hidden]="collapsed()">{{ group.title }}</p>
              @if (collapsed() && !$first) { <div class="mx-auto mb-3 hidden h-px w-8 bg-slate-200 lg:block"></div> }
              <div class="space-y-0.5">
                @for (item of group.items; track item.label) {
                  <a [routerLink]="item.path" routerLinkActive="!bg-brand-50 !text-brand-700 [&>svg]:!text-brand-600"
                     class="group relative flex h-9 items-center gap-3 rounded-lg px-3 text-[13.5px] font-medium text-slate-600 transition-colors hover:bg-slate-100 hover:text-slate-900"
                     [class.lg:justify-center]="collapsed()" [class.lg:px-0]="collapsed()" [attr.title]="collapsed() ? item.label : null">
                    <svg class="h-[18px] w-[18px] flex-none text-slate-400 group-hover:text-slate-600" fill="none" stroke="currentColor" stroke-width="1.8" viewBox="0 0 24 24">
                      <path [attr.d]="icons[item.icon]" stroke-linecap="round" stroke-linejoin="round"/>
                    </svg>
                    <span class="truncate" [class.lg:hidden]="collapsed()">{{ item.label }}</span>
                  </a>
                }
              </div>
            </div>
          }
        </nav>

        <!-- Utilisateur -->
        <div class="relative flex-none border-t border-slate-200 p-3">
          <button class="flex w-full items-center gap-3 rounded-lg p-2 text-left hover:bg-slate-100" [class.lg:justify-center]="collapsed()"
                  (click)="userMenu.set(!userMenu())" aria-label="Menu du compte">
            <span class="flex h-8 w-8 flex-none items-center justify-center rounded-full bg-gradient-to-br from-brand-500 to-indigo-600 text-xs font-semibold text-white">{{ initials() }}</span>
            <span class="min-w-0 flex-1" [class.lg:hidden]="collapsed()">
              <span class="block truncate text-[13px] font-medium text-slate-900">{{ auth.user()?.fullName }}</span>
              <span class="block truncate text-xs text-slate-500">{{ auth.user()?.roleLabel }}</span>
            </span>
          </button>
          @if (userMenu()) {
            <div class="absolute bottom-full left-3 z-50 mb-2 w-56 animate-fade-in overflow-hidden rounded-xl border border-slate-200 bg-white py-1 shadow-pop">
              <div class="border-b border-slate-100 px-4 py-2.5">
                <p class="truncate text-sm font-medium text-slate-900">{{ auth.user()?.fullName }}</p>
                <p class="truncate text-xs text-slate-500">{{ auth.user()?.email }}</p>
              </div>
              <a routerLink="/profile" class="flex items-center gap-2.5 px-4 py-2 text-sm text-slate-700 hover:bg-slate-50" (click)="userMenu.set(false)">
                <svg class="h-4 w-4 text-slate-400" fill="none" stroke="currentColor" stroke-width="1.8" viewBox="0 0 24 24"><path [attr.d]="icons['user']" stroke-linecap="round"/></svg>Mon profil
              </a>
              <button class="flex w-full items-center gap-2.5 px-4 py-2 text-left text-sm text-red-600 hover:bg-red-50" (click)="signOut()">
                <svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="1.8" viewBox="0 0 24 24"><path [attr.d]="icons['logout']" stroke-linecap="round" stroke-linejoin="round"/></svg>Se déconnecter
              </button>
            </div>
          }
          <button class="mt-1 hidden w-full items-center gap-3 rounded-lg px-3 py-1.5 text-xs text-slate-400 hover:bg-slate-100 hover:text-slate-600 lg:flex"
                  [class.justify-center]="collapsed()" (click)="toggleCollapse()" [attr.aria-label]="collapsed() ? 'Déplier le menu' : 'Replier le menu'">
            <svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="1.8" viewBox="0 0 24 24"><path [attr.d]="icons[collapsed() ? 'expand' : 'collapse']" stroke-linecap="round" stroke-linejoin="round"/></svg>
            @if (!collapsed()) { Replier le menu }
          </button>
        </div>
      </aside>

      <!-- Contenu -->
      <div class="flex min-w-0 flex-1 flex-col">
        <header class="sticky top-0 z-20 flex h-16 items-center gap-3 border-b border-slate-200/80 bg-white/85 px-4 backdrop-blur sm:px-6 lg:px-8">
          <button class="btn-ghost -ml-2 lg:hidden" (click)="menuOpen.set(true)" aria-label="Ouvrir le menu">
            <svg class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path [attr.d]="icons['menu']" stroke-linecap="round"/></svg>
          </button>
          <p class="hidden truncate text-sm font-medium text-slate-500 md:block">{{ pageTitle() }}</p>

          <button class="ml-auto flex h-9 w-full max-w-[22rem] items-center gap-2 rounded-lg border border-slate-200 bg-slate-50 px-3 text-sm text-slate-400 transition hover:border-slate-300 hover:bg-white md:ml-6"
                  (click)="paletteOpen.set(true)">
            <svg class="h-4 w-4 flex-none" fill="none" stroke="currentColor" stroke-width="1.8" viewBox="0 0 24 24"><path [attr.d]="icons['search']" stroke-linecap="round"/></svg>
            <span class="flex-1 truncate text-left">Rechercher…</span>
            <kbd class="hidden rounded border border-slate-200 bg-white px-1.5 text-[11px] font-medium text-slate-400 sm:block">Ctrl K</kbd>
          </button>

          <div class="flex flex-none items-center gap-1 md:ml-auto">
            @if (quickActions().length) {
              <div class="relative">
                <button class="btn-primary h-9 px-3" (click)="createOpen.set(!createOpen()); bellOpen.set(false)" aria-label="Créer">
                  <svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2.2" viewBox="0 0 24 24"><path [attr.d]="icons['plus']" stroke-linecap="round"/></svg>
                  <span class="hidden sm:inline">Créer</span>
                </button>
                @if (createOpen()) {
                  <div class="absolute right-0 z-50 mt-2 w-56 animate-fade-in overflow-hidden rounded-xl border border-slate-200 bg-white py-1 shadow-pop">
                    @for (a of quickActions(); track a.path) {
                      <a [routerLink]="a.path" class="flex items-center gap-2.5 px-4 py-2 text-sm text-slate-700 hover:bg-slate-50" (click)="createOpen.set(false)">
                        <svg class="h-4 w-4 text-slate-400" fill="none" stroke="currentColor" stroke-width="1.8" viewBox="0 0 24 24"><path [attr.d]="icons[a.icon]" stroke-linecap="round" stroke-linejoin="round"/></svg>
                        {{ a.label }}
                      </a>
                    }
                  </div>
                }
              </div>
            }

            <div class="relative">
              <button class="btn-ghost relative h-9 w-9 px-0" (click)="bellOpen.set(!bellOpen()); createOpen.set(false)" aria-label="Notifications">
                <svg class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="1.8" viewBox="0 0 24 24"><path [attr.d]="icons['bell']" stroke-linecap="round" stroke-linejoin="round"/></svg>
                @if (realtime.unread()) {
                  <span class="absolute right-1 top-1 flex h-4 min-w-[1rem] items-center justify-center rounded-full bg-red-500 px-1 text-[10px] font-semibold text-white ring-2 ring-white">{{ realtime.unread() > 9 ? '9+' : realtime.unread() }}</span>
                }
              </button>
              @if (bellOpen()) {
                <div class="absolute right-0 z-50 mt-2 w-[22rem] max-w-[calc(100vw-2rem)] animate-fade-in overflow-hidden rounded-xl border border-slate-200 bg-white shadow-pop">
                  <div class="flex items-center justify-between border-b border-slate-100 px-4 py-3">
                    <span class="flex items-center gap-2 text-sm font-semibold text-slate-900">
                      Notifications
                      <span class="h-1.5 w-1.5 rounded-full" [class]="realtime.connected() ? 'bg-emerald-500' : 'bg-slate-300'" [title]="realtime.connected() ? 'Temps réel connecté' : 'Hors ligne'"></span>
                    </span>
                    <button class="text-xs font-medium text-brand-600 hover:text-brand-700" (click)="realtime.markAllRead()">Tout marquer comme lu</button>
                  </div>
                  <ul class="max-h-96 overflow-y-auto">
                    @for (n of realtime.latest(); track n.id) {
                      <li>
                        <button class="flex w-full gap-3 px-4 py-3 text-left hover:bg-slate-50" (click)="openNotification(n)">
                          <span class="mt-1.5 h-2 w-2 flex-none rounded-full" [class]="n.readAt ? 'bg-transparent' : 'bg-brand-500'"></span>
                          <span class="min-w-0">
                            <span class="block text-sm font-medium text-slate-900">{{ n.title }}</span>
                            @if (n.message) { <span class="line-clamp-2 block text-xs text-slate-500">{{ n.message }}</span> }
                            <span class="mt-0.5 block text-[11px] text-slate-400">{{ n.createdAt | date: 'd MMM, HH:mm' }}</span>
                          </span>
                        </button>
                      </li>
                    } @empty {
                      <li class="px-4 py-10 text-center text-sm text-slate-500">Vous êtes à jour.</li>
                    }
                  </ul>
                </div>
              }
            </div>
          </div>
        </header>

        <main class="mx-auto w-full max-w-[1400px] flex-1 animate-fade-in p-4 sm:p-6 lg:p-8">
          <router-outlet />
        </main>
      </div>
    </div>

    @if (paletteOpen()) {
      <app-command-palette [pages]="pageItems()" (closed)="paletteOpen.set(false)" />
    }
    @if (createOpen() || bellOpen()) {
      <div class="fixed inset-0 z-10" (click)="createOpen.set(false); bellOpen.set(false)"></div>
    }
  `
})
export class ShellComponent implements OnInit {
  auth = inject(AuthService);
  realtime = inject(RealtimeService);
  private router = inject(Router);
  icons = ICONS;
  menuOpen = signal(false);
  userMenu = signal(false);
  bellOpen = signal(false);
  createOpen = signal(false);
  paletteOpen = signal(false);
  collapsed = signal(this.readCollapsed());
  pageTitle = signal('');

  private readonly groups: { title: string; items: NavItem[] }[] = [
    {
      title: 'Ventes',
      items: [
        { label: 'Tableau de bord', path: '/dashboard', icon: 'home' },
        { label: 'Leads', path: '/leads', icon: 'target', permission: 'LEAD_MANAGE' },
        { label: 'Pipeline', path: '/pipeline', icon: 'columns', permission: 'OPPORTUNITY_MANAGE' },
        { label: 'Devis', path: '/quotes', icon: 'file', permission: 'QUOTE_MANAGE' },
        { label: 'Validations de devis', path: '/quotes', icon: 'check', permission: 'QUOTE_APPROVE', onlyWithout: 'QUOTE_MANAGE' },
        { label: 'Agenda', path: '/activities', icon: 'calendar', permission: 'ACTIVITY_MANAGE' }
      ]
    },
    {
      title: 'Clients',
      items: [
        { label: 'Entreprises', path: '/companies', icon: 'building', permission: 'CONTACT_READ' },
        { label: 'Contacts', path: '/contacts', icon: 'users', permission: 'CONTACT_READ' },
        { label: 'Tickets SAV', path: '/tickets', icon: 'lifebuoy', permission: 'TICKET_MANAGE' }
      ]
    },
    { title: 'Pilotage', items: [{ label: 'Objectifs', path: '/objectives', icon: 'flag', permission: 'OBJECTIVE_MANAGE' }] },
    {
      title: 'Administration',
      items: [
        { label: 'Utilisateurs et rôles', path: '/users', icon: 'shield', permission: 'USER_MANAGE' },
        { label: 'Journal', path: '/audit', icon: 'list', permission: 'AUDIT_READ' },
        { label: 'Paramètres', path: '/settings', icon: 'cog', permission: 'SETTINGS_MANAGE' }
      ]
    }
  ];

  private readonly actions: QuickAction[] = [
    { label: 'Nouveau lead', path: '/leads/new', icon: 'target', permission: 'LEAD_MANAGE' },
    { label: 'Nouvelle opportunité', path: '/opportunities/new', icon: 'columns', permission: 'OPPORTUNITY_MANAGE' },
    { label: 'Nouvelle entreprise', path: '/companies/new', icon: 'building', permission: 'CONTACT_WRITE' },
    { label: 'Nouveau contact', path: '/contacts/new', icon: 'users', permission: 'CONTACT_WRITE' }
  ];

  private allowed = (p?: Permission | Permission[]) => !p || this.auth.hasPermission(...(Array.isArray(p) ? p : [p]));

  navGroups = computed(() => {
    this.auth.user();
    return this.groups
      .map((g) => ({
        ...g,
        items: g.items.filter((i) => this.allowed(i.permission) && (!i.onlyWithout || !this.auth.hasPermission(i.onlyWithout)))
      }))
      .filter((g) => g.items.length > 0);
  });

  quickActions = computed(() => {
    this.auth.user();
    return this.actions.filter((a) => this.allowed(a.permission));
  });

  pageItems = computed<PaletteItem[]>(() => [
    ...this.navGroups().flatMap((g) => g.items.map((i) => ({ group: 'Pages', label: i.label, icon: i.icon, link: i.path }))),
    ...this.quickActions().map((a) => ({ group: 'Actions', label: a.label, icon: 'plus', link: a.path })),
    { group: 'Pages', label: 'Mon profil', icon: 'user', link: '/profile' }
  ]);

  initials = computed(() => {
    const u = this.auth.user();
    return u ? (u.firstName[0] + u.lastName[0]).toUpperCase() : '';
  });

  ngOnInit(): void {
    this.updateTitle();
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => {
      this.menuOpen.set(false);
      this.userMenu.set(false);
      this.bellOpen.set(false);
      this.createOpen.set(false);
      this.updateTitle();
    });
    this.realtime.start();
    // Synchronise rôle et permissions avec le serveur au chargement.
    this.auth.refreshMe().subscribe({ error: () => {} });
  }

  @HostListener('document:keydown', ['$event'])
  onKeydown(e: KeyboardEvent): void {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
      e.preventDefault();
      this.paletteOpen.set(!this.paletteOpen());
    } else if (e.key === 'Escape') {
      this.userMenu.set(false);
      this.bellOpen.set(false);
      this.createOpen.set(false);
    }
  }

  private updateTitle(): void {
    let snap: ActivatedRouteSnapshot | null = this.router.routerState.snapshot.root;
    let title = '';
    while (snap) {
      title = snap.title ?? title;
      snap = snap.firstChild;
    }
    this.pageTitle.set(title);
  }

  toggleCollapse(): void {
    this.collapsed.set(!this.collapsed());
    try {
      localStorage.setItem(COLLAPSE_KEY, String(this.collapsed()));
    } catch {
      /* stockage indisponible : préférence non mémorisée */
    }
  }

  private readCollapsed(): boolean {
    try {
      return localStorage.getItem(COLLAPSE_KEY) === 'true';
    } catch {
      return false;
    }
  }

  openNotification(n: AppNotification): void {
    this.realtime.markRead(n);
    this.bellOpen.set(false);
    if (n.link) this.router.navigateByUrl(n.link);
  }

  signOut(): void {
    this.realtime.stop();
    this.auth.signOut();
  }
}
