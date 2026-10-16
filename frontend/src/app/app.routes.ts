import { Routes } from '@angular/router';
import { authGuard, guestGuard, permissionGuard, portalGuard, staffGuard } from './core/guards';
import { ShellComponent } from './layout/shell.component';

export const routes: Routes = [
  // ---- Pages publiques ----
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login.component').then((m) => m.LoginComponent)
  },
  {
    path: 'forgot-password',
    loadComponent: () => import('./features/auth/forgot-password.component').then((m) => m.ForgotPasswordComponent)
  },
  {
    path: 'reset-password',
    loadComponent: () => import('./features/auth/reset-password.component').then((m) => m.ResetPasswordComponent)
  },
  {
    path: 'contact',
    title: 'Demande de contact',
    loadComponent: () => import('./features/public/public-pages.component').then((m) => m.LeadCaptureComponent)
  },
  {
    path: 'confidentialite',
    title: 'Protection des données',
    loadComponent: () => import('./features/public/public-pages.component').then((m) => m.PrivacyPolicyComponent)
  },

  // ---- Portail client ----
  {
    path: 'portal',
    canActivate: [authGuard, portalGuard],
    loadComponent: () => import('./features/portal/portal-shell.component').then((m) => m.PortalShellComponent),
    children: [
      { path: '', title: 'Espace client', loadComponent: () => import('./features/portal/portal-pages.component').then((m) => m.PortalHomeComponent) },
      { path: 'quotes', title: 'Mes devis', loadComponent: () => import('./features/portal/portal-pages.component').then((m) => m.PortalQuotesComponent) },
      { path: 'invoices', title: 'Mes factures', loadComponent: () => import('./features/portal/portal-pages.component').then((m) => m.PortalInvoicesComponent) },
      { path: 'tickets', title: 'Mes demandes', loadComponent: () => import('./features/portal/portal-pages.component').then((m) => m.PortalTicketsComponent) },
      { path: 'tickets/new', title: 'Nouvelle demande', loadComponent: () => import('./features/portal/portal-pages.component').then((m) => m.PortalTicketNewComponent) },
      { path: 'tickets/:id', title: 'Ma demande', loadComponent: () => import('./features/portal/portal-pages.component').then((m) => m.PortalTicketComponent) }
    ]
  },

  // ---- Application interne ----
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard, staffGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        title: 'Tableau de bord',
        loadComponent: () => import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent)
      },
      {
        path: 'objectives',
        title: 'Objectifs',
        canActivate: [permissionGuard('OBJECTIVE_MANAGE', 'DASHBOARD_ALL')],
        loadComponent: () => import('./features/dashboard/objectives.component').then((m) => m.ObjectivesComponent)
      },
      {
        path: 'companies',
        canActivate: [permissionGuard('CONTACT_READ')],
        children: [
          { path: '', title: 'Entreprises', loadComponent: () => import('./features/companies/company-list.component').then((m) => m.CompanyListComponent) },
          { path: 'new', title: 'Nouvelle entreprise', canActivate: [permissionGuard('CONTACT_WRITE')], loadComponent: () => import('./features/companies/company-form.component').then((m) => m.CompanyFormComponent) },
          { path: ':id', title: 'Fiche client', loadComponent: () => import('./features/companies/company-detail.component').then((m) => m.CompanyDetailComponent) },
          { path: ':id/edit', title: 'Modifier l’entreprise', canActivate: [permissionGuard('CONTACT_WRITE')], loadComponent: () => import('./features/companies/company-form.component').then((m) => m.CompanyFormComponent) }
        ]
      },
      {
        path: 'contacts',
        canActivate: [permissionGuard('CONTACT_READ')],
        children: [
          { path: '', title: 'Contacts', loadComponent: () => import('./features/contacts/contact-list.component').then((m) => m.ContactListComponent) },
          { path: 'new', title: 'Nouveau contact', canActivate: [permissionGuard('CONTACT_WRITE')], loadComponent: () => import('./features/contacts/contact-form.component').then((m) => m.ContactFormComponent) },
          { path: ':id/edit', title: 'Modifier le contact', canActivate: [permissionGuard('CONTACT_WRITE')], loadComponent: () => import('./features/contacts/contact-form.component').then((m) => m.ContactFormComponent) }
        ]
      },
      {
        path: 'leads',
        canActivate: [permissionGuard('LEAD_MANAGE')],
        children: [
          { path: '', title: 'Leads', loadComponent: () => import('./features/leads/lead-list.component').then((m) => m.LeadListComponent) },
          { path: 'new', title: 'Nouveau lead', loadComponent: () => import('./features/leads/lead-form.component').then((m) => m.LeadFormComponent) },
          { path: ':id', title: 'Lead', loadComponent: () => import('./features/leads/lead-detail.component').then((m) => m.LeadDetailComponent) },
          { path: ':id/edit', title: 'Modifier le lead', loadComponent: () => import('./features/leads/lead-form.component').then((m) => m.LeadFormComponent) }
        ]
      },
      {
        path: 'pipeline',
        title: 'Pipeline',
        canActivate: [permissionGuard('OPPORTUNITY_MANAGE', 'DASHBOARD_ALL')],
        loadComponent: () => import('./features/opportunities/pipeline.component').then((m) => m.PipelineComponent)
      },
      {
        path: 'opportunities',
        canActivate: [permissionGuard('OPPORTUNITY_MANAGE', 'DASHBOARD_ALL')],
        children: [
          { path: '', pathMatch: 'full', redirectTo: '/pipeline' },
          { path: 'new', title: 'Nouvelle opportunité', loadComponent: () => import('./features/opportunities/opportunity-form.component').then((m) => m.OpportunityFormComponent) },
          { path: ':id', title: 'Opportunité', loadComponent: () => import('./features/opportunities/opportunity-detail.component').then((m) => m.OpportunityDetailComponent) },
          { path: ':id/edit', title: 'Modifier l’opportunité', loadComponent: () => import('./features/opportunities/opportunity-form.component').then((m) => m.OpportunityFormComponent) }
        ]
      },
      {
        path: 'quotes',
        canActivate: [permissionGuard('QUOTE_MANAGE', 'QUOTE_APPROVE')],
        children: [
          { path: '', title: 'Devis', loadComponent: () => import('./features/quotes/quote-list.component').then((m) => m.QuoteListComponent) },
          { path: 'new', title: 'Nouveau devis', loadComponent: () => import('./features/quotes/quote-editor.component').then((m) => m.QuoteEditorComponent) },
          { path: ':id', title: 'Devis', loadComponent: () => import('./features/quotes/quote-detail.component').then((m) => m.QuoteDetailComponent) },
          { path: ':id/edit', title: 'Modifier le devis', loadComponent: () => import('./features/quotes/quote-editor.component').then((m) => m.QuoteEditorComponent) }
        ]
      },
      {
        path: 'products',
        title: 'Catalogue',
        canActivate: [permissionGuard('QUOTE_MANAGE', 'PRODUCT_MANAGE')],
        loadComponent: () => import('./features/quotes/product-list.component').then((m) => m.ProductListComponent)
      },
      {
        path: 'invoices',
        title: 'Factures',
        canActivate: [permissionGuard('QUOTE_MANAGE', 'DASHBOARD_ALL')],
        loadComponent: () => import('./features/quotes/invoice-list.component').then((m) => m.InvoiceListComponent)
      },
      {
        path: 'activities',
        title: 'Agenda',
        canActivate: [permissionGuard('ACTIVITY_MANAGE')],
        loadComponent: () => import('./features/activities/activities.component').then((m) => m.ActivitiesComponent)
      },
      {
        path: 'tickets',
        canActivate: [permissionGuard('TICKET_MANAGE', 'CONTACT_READ')],
        children: [
          { path: '', title: 'Tickets', loadComponent: () => import('./features/tickets/ticket-list.component').then((m) => m.TicketListComponent) },
          { path: ':id', title: 'Ticket', loadComponent: () => import('./features/tickets/ticket-detail.component').then((m) => m.TicketDetailComponent) }
        ]
      },
      {
        path: 'users',
        title: 'Utilisateurs',
        canActivate: [permissionGuard('USER_MANAGE')],
        loadComponent: () => import('./features/users/user-list.component').then((m) => m.UserListComponent)
      },
      {
        path: 'audit',
        title: 'Journal',
        canActivate: [permissionGuard('AUDIT_READ')],
        loadComponent: () => import('./features/admin/audit.component').then((m) => m.AuditComponent)
      },
      {
        path: 'settings',
        title: 'Paramètres',
        canActivate: [permissionGuard('SETTINGS_MANAGE')],
        loadComponent: () => import('./features/admin/settings.component').then((m) => m.SettingsComponent)
      },
      {
        path: 'profile',
        title: 'Mon profil',
        loadComponent: () => import('./features/profile/profile.component').then((m) => m.ProfileComponent)
      }
    ]
  },
  { path: '**', redirectTo: '' }
];
