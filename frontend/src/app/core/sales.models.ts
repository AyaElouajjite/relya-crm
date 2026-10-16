import {
  ActivityStatus,
  ActivityType,
  InvoiceStatus,
  LeadSource,
  LeadStatus,
  LossReason,
  OpportunityStage,
  ProductType,
  QuoteStatus,
  Ref,
  TicketPriority,
  TicketStatus,
  UserSummary
} from './models';

export interface Lead {
  id: number;
  firstName: string;
  lastName: string;
  fullName: string;
  email: string | null;
  phone: string | null;
  companyName: string | null;
  position: string | null;
  sector: string | null;
  city: string | null;
  source: LeadSource;
  status: LeadStatus;
  score: number | null;
  scoredAt: string | null;
  estimatedBudget: number | null;
  notes: string | null;
  marketingConsent: boolean;
  assignedTo: UserSummary | null;
  convertedCompany: Ref | null;
  convertedOpportunity: Ref | null;
  convertedAt: string | null;
  createdAt: string;
}

export interface Opportunity {
  id: number;
  title: string;
  amount: number;
  probability: number;
  weightedAmount: number;
  stage: OpportunityStage;
  expectedCloseDate: string | null;
  closedAt: string | null;
  lossReason: LossReason | null;
  lossComment: string | null;
  company: Ref;
  contact: Ref | null;
  owner: UserSummary | null;
  description: string | null;
  lastActivityAt: string | null;
  createdAt: string;
}

export interface PipelineColumn {
  stage: OpportunityStage;
  count: number;
  total: number;
  weighted: number;
  items: Opportunity[];
}

export interface Product {
  id: number;
  name: string;
  reference: string | null;
  description: string | null;
  type: ProductType;
  priceHT: number;
  vatRate: number;
  unit: string | null;
  active: boolean;
}

export interface QuoteLine {
  id?: number;
  productId: number | null;
  description: string;
  quantity: number;
  unitPrice: number;
  discount: number;
  vatRate: number;
  totalHT?: number;
}

export interface Quote {
  id: number;
  number: string;
  date: string;
  validUntil: string | null;
  status: QuoteStatus;
  opportunity: Ref;
  company: Ref;
  contact: Ref | null;
  contactEmail: string | null;
  contactPhone: string | null;
  owner: UserSummary | null;
  lines: QuoteLine[];
  totalHT: number;
  totalVAT: number;
  totalTTC: number;
  notes: string | null;
  approvedBy: UserSummary | null;
  approvedAt: string | null;
  sentAt: string | null;
  respondedAt: string | null;
  needsApproval: boolean;
  publicToken: string | null;
  invoice: Ref | null;
  createdAt: string;
}

export interface QuoteSummary {
  id: number;
  number: string;
  date: string;
  status: QuoteStatus;
  company: Ref;
  opportunity: Ref;
  owner: UserSummary | null;
  totalTTC: number;
}

export interface Invoice {
  id: number;
  number: string;
  quote: Ref;
  company: Ref;
  issueDate: string;
  dueDate: string | null;
  paidDate: string | null;
  status: InvoiceStatus;
  totalHT: number;
  totalVAT: number;
  totalTTC: number;
  overdue: boolean;
}

export interface Activity {
  id: number;
  type: ActivityType;
  subject: string;
  description: string | null;
  dueAt: string;
  durationMinutes: number | null;
  status: ActivityStatus;
  completedAt: string | null;
  contact: Ref | null;
  company: Ref | null;
  opportunity: Ref | null;
  lead: Ref | null;
  owner: UserSummary | null;
  reminderAt: string | null;
  automatic: boolean;
  overdue: boolean;
  createdAt: string;
}

export interface TicketMessage {
  id: number;
  author: string;
  fromClient: boolean;
  content: string;
  internal: boolean;
  createdAt: string;
}

export interface Ticket {
  id: number;
  number: string;
  subject: string;
  description: string | null;
  priority: TicketPriority;
  status: TicketStatus;
  channel: string;
  category: string | null;
  contact: Ref;
  company: Ref;
  assignee: UserSummary | null;
  openedAt: string;
  dueAt: string;
  firstResponseAt: string | null;
  resolvedAt: string | null;
  overdue: boolean;
  messages: TicketMessage[];
}

export interface AppNotification {
  id: number;
  type: string;
  title: string;
  message: string | null;
  link: string | null;
  readAt: string | null;
  createdAt: string;
}

export interface Slice {
  key: string;
  count: number;
  amount: number | null;
}

export interface Dashboard {
  from: string;
  to: string;
  ownerId: number | null;
  kpis: {
    revenue: number;
    objective: number;
    objectiveRate: number | null;
    forecast: number;
    pipelineTotal: number;
    openOpportunities: number;
    won: number;
    lost: number;
    winRate: number | null;
    leadsCreated: number;
    leadsConverted: number;
    leadConversionRate: number | null;
    quotesSent: number;
    quoteAcceptanceRate: number | null;
    openTickets: number;
    overdueTickets: number;
    avgResolutionHours: number | null;
    activitiesDone: number;
    overdueActivities: number;
  };
  monthly: { month: number; revenue: number; objective: number }[];
  sellers: {
    userId: number;
    name: string;
    revenue: number;
    objective: number;
    objectiveRate: number | null;
    forecast: number;
    won: number;
    lost: number;
    winRate: number | null;
    leadsConverted: number;
    leadConversionRate: number | null;
    activitiesDone: number;
  }[];
  pipelineByStage: Slice[];
  lossReasons: Slice[];
  leadSources: Slice[];
  ticketsByStatus: Slice[];
  ticketsByPriority: Slice[];
}

export interface ObjectiveRow {
  user: UserSummary;
  months: (number | null)[];
}

export interface EmailDraft {
  subject: string;
  body: string;
  model: string;
  generated: boolean;
  to: string | null;
}

// ---- Libellés français ----

export const LEAD_SOURCE_LABELS: Record<LeadSource, string> = {
  MANUEL: 'Saisie manuelle',
  FORMULAIRE_WEB: 'Formulaire web',
  IMPORT: 'Import',
  SALON: 'Salon',
  RECOMMANDATION: 'Recommandation',
  RESEAUX_SOCIAUX: 'Réseaux sociaux',
  AUTRE: 'Autre'
};

export const LEAD_STATUS_LABELS: Record<LeadStatus, string> = {
  NOUVEAU: 'Nouveau',
  CONTACTE: 'Contacté',
  QUALIFIE: 'Qualifié',
  NON_QUALIFIE: 'Non qualifié',
  CONVERTI: 'Converti'
};

export const STAGE_LABELS: Record<OpportunityStage, string> = {
  PROSPECTION: 'Prospection',
  QUALIFICATION: 'Qualification',
  PROPOSITION: 'Proposition',
  NEGOCIATION: 'Négociation',
  GAGNEE: 'Gagnée',
  PERDUE: 'Perdue'
};

export const LOSS_REASON_LABELS: Record<LossReason, string> = {
  PRIX: 'Prix trop élevé',
  CONCURRENT: 'Choix d’un concurrent',
  BUDGET: 'Pas de budget',
  TIMING: 'Mauvais moment',
  BESOIN_NON_CONFIRME: 'Besoin non confirmé',
  SANS_REPONSE: 'Plus de réponse',
  AUTRE: 'Autre'
};

export const QUOTE_STATUS_LABELS: Record<QuoteStatus, string> = {
  BROUILLON: 'Brouillon',
  EN_VALIDATION: 'En validation',
  VALIDE: 'Validé',
  ENVOYE: 'Envoyé',
  ACCEPTE: 'Accepté',
  REFUSE: 'Refusé'
};

export const INVOICE_STATUS_LABELS: Record<InvoiceStatus, string> = {
  EMISE: 'À régler',
  PAYEE: 'Payée',
  ANNULEE: 'Annulée'
};

export const ACTIVITY_TYPE_LABELS: Record<ActivityType, string> = {
  APPEL: 'Appel',
  EMAIL: 'E-mail',
  REUNION: 'Rendez-vous',
  TACHE: 'Tâche',
  NOTE: 'Note',
  SYSTEME: 'Historique'
};

export const ACTIVITY_STATUS_LABELS: Record<ActivityStatus, string> = {
  A_FAIRE: 'À faire',
  TERMINEE: 'Terminée',
  ANNULEE: 'Annulée'
};

export const TICKET_PRIORITY_LABELS: Record<TicketPriority, string> = {
  BASSE: 'Basse',
  NORMALE: 'Normale',
  HAUTE: 'Haute',
  URGENTE: 'Urgente'
};

export const TICKET_STATUS_LABELS: Record<TicketStatus, string> = {
  OUVERT: 'Ouvert',
  EN_COURS: 'En cours',
  EN_ATTENTE_CLIENT: 'En attente client',
  RESOLU: 'Résolu',
  FERME: 'Fermé'
};

/** Classes Tailwind des pastilles de statut (couleur + libellé, jamais la couleur seule). */
export const BADGE: Record<string, string> = {
  // devis
  BROUILLON: 'bg-slate-100 text-slate-700',
  EN_VALIDATION: 'bg-amber-50 text-amber-800',
  VALIDE: 'bg-sky-50 text-sky-800',
  ENVOYE: 'bg-brand-50 text-brand-700',
  ACCEPTE: 'bg-emerald-50 text-emerald-800',
  REFUSE: 'bg-red-50 text-red-700',
  // factures
  EMISE: 'bg-amber-50 text-amber-800',
  PAYEE: 'bg-emerald-50 text-emerald-800',
  ANNULEE: 'bg-slate-100 text-slate-600',
  // leads
  NOUVEAU: 'bg-brand-50 text-brand-700',
  CONTACTE: 'bg-sky-50 text-sky-800',
  QUALIFIE: 'bg-emerald-50 text-emerald-800',
  NON_QUALIFIE: 'bg-slate-100 text-slate-600',
  CONVERTI: 'bg-violet-50 text-violet-800',
  // tickets
  OUVERT: 'bg-brand-50 text-brand-700',
  EN_COURS: 'bg-sky-50 text-sky-800',
  EN_ATTENTE_CLIENT: 'bg-amber-50 text-amber-800',
  RESOLU: 'bg-emerald-50 text-emerald-800',
  FERME: 'bg-slate-100 text-slate-600',
  // priorités
  BASSE: 'bg-slate-100 text-slate-600',
  NORMALE: 'bg-sky-50 text-sky-800',
  HAUTE: 'bg-amber-50 text-amber-800',
  URGENTE: 'bg-red-50 text-red-700',
  // activités
  A_FAIRE: 'bg-amber-50 text-amber-800',
  TERMINEE: 'bg-emerald-50 text-emerald-800'
};
