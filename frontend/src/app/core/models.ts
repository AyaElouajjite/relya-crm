export type Permission =
  | 'USER_MANAGE'
  | 'SETTINGS_MANAGE'
  | 'AUDIT_READ'
  | 'DATA_PRIVACY'
  | 'CONTACT_READ'
  | 'CONTACT_WRITE'
  | 'CONTACT_DELETE'
  | 'LEAD_MANAGE'
  | 'LEAD_ASSIGN'
  | 'OPPORTUNITY_MANAGE'
  | 'PRODUCT_MANAGE'
  | 'QUOTE_MANAGE'
  | 'QUOTE_APPROVE'
  | 'ACTIVITY_MANAGE'
  | 'ALL_RECORDS'
  | 'DASHBOARD_VIEW'
  | 'DASHBOARD_ALL'
  | 'OBJECTIVE_MANAGE'
  | 'TICKET_MANAGE'
  | 'PORTAL_ACCESS'
  | 'DATA_EXPORT'
  | 'DATA_IMPORT';

export type RoleCode = 'ADMIN' | 'DIRECTEUR' | 'COMMERCIAL' | 'SUPPORT' | 'CLIENT';

export interface User {
  id: number;
  firstName: string;
  lastName: string;
  fullName: string;
  email: string;
  phone: string | null;
  active: boolean;
  roleCode: RoleCode;
  roleLabel: string;
  permissions: Permission[];
  lastLoginAt: string | null;
  createdAt: string;
}

export interface UserSummary {
  id: number;
  fullName: string;
  roleCode: RoleCode;
}

export interface Role {
  id: number;
  code: RoleCode;
  label: string;
  permissions: Permission[];
}

export interface AuthResponse {
  token: string;
  expiresAt: string;
  user: User;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type CompanyStatus = 'PROSPECT' | 'CLIENT' | 'INACTIF';

export const COMPANY_STATUS_LABELS: Record<CompanyStatus, string> = {
  PROSPECT: 'Prospect',
  CLIENT: 'Client',
  INACTIF: 'Inactif'
};

export interface Company {
  id: number;
  name: string;
  sector: string | null;
  ice: string | null;
  city: string | null;
  address: string | null;
  phone: string | null;
  email: string | null;
  website: string | null;
  status: CompanyStatus;
  tags: string[];
  owner: UserSummary | null;
  archived: boolean;
  contactCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface CompanyRequest {
  name: string;
  sector?: string | null;
  ice?: string | null;
  city?: string | null;
  address?: string | null;
  phone?: string | null;
  email?: string | null;
  website?: string | null;
  status?: CompanyStatus;
  tags?: string[];
  ownerId?: number | null;
}

export interface Company360 {
  company: Company;
  contacts: Contact[];
  opportunities: import('./sales.models').Opportunity[];
  quotes: import('./sales.models').QuoteSummary[];
  invoices: import('./sales.models').Invoice[];
  tickets: import('./sales.models').Ticket[];
  history: import('./sales.models').Activity[];
  revenue: number;
  openPipeline: number;
}

export interface Contact {
  id: number;
  firstName: string;
  lastName: string;
  fullName: string;
  email: string | null;
  phone: string | null;
  position: string | null;
  companyId: number | null;
  companyName: string | null;
  tags: string[];
  owner: UserSummary | null;
  archived: boolean;
  marketingConsent: boolean;
  consentDate: string | null;
  anonymized: boolean;
  portalAccess: boolean;
  createdAt: string;
}

export interface ContactRequest {
  firstName: string;
  lastName: string;
  email?: string | null;
  phone?: string | null;
  position?: string | null;
  companyId?: number | null;
  tags?: string[];
  ownerId?: number | null;
  marketingConsent?: boolean;
}

export interface ImportResult {
  created: number;
  updated: number;
  errors: string[];
}

export interface LoginLog {
  id: number;
  email: string;
  success: boolean;
  ipAddress: string | null;
  userAgent: string | null;
  at: string;
}

export interface AuditLog {
  id: number;
  actor: string;
  action: string;
  entityType: string;
  entityId: number | null;
  details: string | null;
  at: string;
}

export interface ApiError {
  status: number;
  message: string;
  errors?: Record<string, string> | null;
}

// ---- Modules 3 à 8 ----

export interface Ref {
  id: number;
  label: string;
}

export type LeadSource = 'MANUEL' | 'FORMULAIRE_WEB' | 'IMPORT' | 'SALON' | 'RECOMMANDATION' | 'RESEAUX_SOCIAUX' | 'AUTRE';
export type LeadStatus = 'NOUVEAU' | 'CONTACTE' | 'QUALIFIE' | 'NON_QUALIFIE' | 'CONVERTI';
export type OpportunityStage = 'PROSPECTION' | 'QUALIFICATION' | 'PROPOSITION' | 'NEGOCIATION' | 'GAGNEE' | 'PERDUE';
export type LossReason = 'PRIX' | 'CONCURRENT' | 'BUDGET' | 'TIMING' | 'BESOIN_NON_CONFIRME' | 'SANS_REPONSE' | 'AUTRE';
export type QuoteStatus = 'BROUILLON' | 'EN_VALIDATION' | 'VALIDE' | 'ENVOYE' | 'ACCEPTE' | 'REFUSE';
export type InvoiceStatus = 'EMISE' | 'PAYEE' | 'ANNULEE';
export type ActivityType = 'APPEL' | 'EMAIL' | 'REUNION' | 'TACHE' | 'NOTE' | 'SYSTEME';
export type ActivityStatus = 'A_FAIRE' | 'TERMINEE' | 'ANNULEE';
export type TicketPriority = 'BASSE' | 'NORMALE' | 'HAUTE' | 'URGENTE';
export type TicketStatus = 'OUVERT' | 'EN_COURS' | 'EN_ATTENTE_CLIENT' | 'RESOLU' | 'FERME';
export type ProductType = 'PRODUIT' | 'SERVICE';
