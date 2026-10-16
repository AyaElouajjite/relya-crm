import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ImportResult, Page, Role, User } from './models';
import {
  Activity,
  AppNotification,
  Dashboard,
  EmailDraft,
  Invoice,
  Lead,
  ObjectiveRow,
  Opportunity,
  PipelineColumn,
  Product,
  Quote,
  QuoteSummary,
  Ticket
} from './sales.models';

export type Params = Record<string, string | number | boolean | null | undefined>;

export function toParams(params: Params): HttpParams {
  let p = new HttpParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== null && value !== undefined && value !== '') {
      p = p.set(key, String(value));
    }
  }
  return p;
}

/** Télécharge (ou ouvre) un fichier renvoyé par l'API. */
export function saveFile(res: HttpResponse<Blob>, fallbackName: string, open = false): void {
  const disposition = res.headers.get('Content-Disposition') ?? '';
  const match = /filename="?([^";]+)"?/.exec(disposition);
  const url = URL.createObjectURL(res.body!);
  if (open) {
    window.open(url, '_blank', 'noopener');
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
    return;
  }
  const a = document.createElement('a');
  a.href = url;
  a.download = match?.[1] ?? fallbackName;
  a.click();
  URL.revokeObjectURL(url);
}

@Injectable({ providedIn: 'root' })
export class FileApi {
  private http = inject(HttpClient);

  /** Ouvre un PDF (devis, facture, rapport) dans un nouvel onglet. */
  openPdf(url: string, params: Params = {}): Observable<void> {
    return this.http
      .get(url, { params: toParams(params), observe: 'response', responseType: 'blob' })
      .pipe(map((res) => saveFile(res, 'document.pdf', true)));
  }

  download(url: string, params: Params = {}, fallback = 'export'): Observable<void> {
    return this.http
      .get(url, { params: toParams(params), observe: 'response', responseType: 'blob' })
      .pipe(map((res) => saveFile(res, fallback)));
  }
}

@Injectable({ providedIn: 'root' })
export class LeadApi {
  private http = inject(HttpClient);
  search(params: Params): Observable<Page<Lead>> {
    return this.http.get<Page<Lead>>('/api/leads', { params: toParams(params) });
  }
  get(id: number): Observable<Lead> {
    return this.http.get<Lead>(`/api/leads/${id}`);
  }
  create(body: unknown): Observable<Lead> {
    return this.http.post<Lead>('/api/leads', body);
  }
  update(id: number, body: unknown): Observable<Lead> {
    return this.http.put<Lead>(`/api/leads/${id}`, body);
  }
  assign(id: number, userId: number): Observable<Lead> {
    return this.http.patch<Lead>(`/api/leads/${id}/assign`, null, { params: { userId } });
  }
  convert(id: number, body: unknown): Observable<{ companyId: number; contactId: number; opportunityId: number | null }> {
    return this.http.post<{ companyId: number; contactId: number; opportunityId: number | null }>(`/api/leads/${id}/convert`, body);
  }
  rescore(id: number): Observable<Lead> {
    return this.http.post<Lead>(`/api/leads/${id}/score`, null);
  }
  rescoreAll(): Observable<{ scored: number }> {
    return this.http.post<{ scored: number }>('/api/leads/score-all', null);
  }
  retrain(): Observable<Record<string, unknown>> {
    return this.http.post<Record<string, unknown>>('/api/leads/model/train', null);
  }
  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/api/leads/${id}`);
  }
  import(file: File): Observable<ImportResult> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ImportResult>('/api/leads/import', form);
  }
}

@Injectable({ providedIn: 'root' })
export class OpportunityApi {
  private http = inject(HttpClient);
  search(params: Params): Observable<Page<Opportunity>> {
    return this.http.get<Page<Opportunity>>('/api/opportunities', { params: toParams(params) });
  }
  pipeline(params: Params): Observable<PipelineColumn[]> {
    return this.http.get<PipelineColumn[]>('/api/opportunities/pipeline', { params: toParams(params) });
  }
  get(id: number): Observable<Opportunity> {
    return this.http.get<Opportunity>(`/api/opportunities/${id}`);
  }
  create(body: unknown): Observable<Opportunity> {
    return this.http.post<Opportunity>('/api/opportunities', body);
  }
  update(id: number, body: unknown): Observable<Opportunity> {
    return this.http.put<Opportunity>(`/api/opportunities/${id}`, body);
  }
  moveStage(id: number, body: { stage: string; lossReason?: string | null; lossComment?: string | null }): Observable<Opportunity> {
    return this.http.patch<Opportunity>(`/api/opportunities/${id}/stage`, body);
  }
  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/api/opportunities/${id}`);
  }
}

@Injectable({ providedIn: 'root' })
export class ProductApi {
  private http = inject(HttpClient);
  search(params: Params): Observable<Page<Product>> {
    return this.http.get<Page<Product>>('/api/products', { params: toParams(params) });
  }
  create(body: unknown): Observable<Product> {
    return this.http.post<Product>('/api/products', body);
  }
  update(id: number, body: unknown): Observable<Product> {
    return this.http.put<Product>(`/api/products/${id}`, body);
  }
}

@Injectable({ providedIn: 'root' })
export class QuoteApi {
  private http = inject(HttpClient);
  search(params: Params): Observable<Page<QuoteSummary>> {
    return this.http.get<Page<QuoteSummary>>('/api/quotes', { params: toParams(params) });
  }
  get(id: number): Observable<Quote> {
    return this.http.get<Quote>(`/api/quotes/${id}`);
  }
  create(body: unknown): Observable<Quote> {
    return this.http.post<Quote>('/api/quotes', body);
  }
  update(id: number, body: unknown): Observable<Quote> {
    return this.http.put<Quote>(`/api/quotes/${id}`, body);
  }
  action(id: number, action: 'send' | 'approve' | 'reject-approval' | 'accept' | 'refuse' | 'duplicate', body: unknown = {}): Observable<Quote> {
    return this.http.post<Quote>(`/api/quotes/${id}/${action}`, body);
  }
  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/api/quotes/${id}`);
  }
  invoices(params: Params): Observable<Page<Invoice>> {
    return this.http.get<Page<Invoice>>('/api/invoices', { params: toParams(params) });
  }
  setInvoiceStatus(id: number, value: string): Observable<Invoice> {
    return this.http.patch<Invoice>(`/api/invoices/${id}/status`, null, { params: { value } });
  }
}

@Injectable({ providedIn: 'root' })
export class ActivityApi {
  private http = inject(HttpClient);
  search(params: Params): Observable<Page<Activity>> {
    return this.http.get<Page<Activity>>('/api/activities', { params: toParams(params) });
  }
  calendar(from: string, to: string, ownerId?: number | null): Observable<Activity[]> {
    return this.http.get<Activity[]>('/api/activities/calendar', { params: toParams({ from, to, ownerId }) });
  }
  feed(): Observable<{ path: string }> {
    return this.http.get<{ path: string }>('/api/activities/calendar-feed');
  }
  create(body: unknown): Observable<Activity> {
    return this.http.post<Activity>('/api/activities', body);
  }
  update(id: number, body: unknown): Observable<Activity> {
    return this.http.put<Activity>(`/api/activities/${id}`, body);
  }
  setStatus(id: number, value: string): Observable<Activity> {
    return this.http.patch<Activity>(`/api/activities/${id}/status`, null, { params: { value } });
  }
  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/api/activities/${id}`);
  }
}

@Injectable({ providedIn: 'root' })
export class TicketApi {
  private http = inject(HttpClient);
  search(params: Params): Observable<Page<Ticket>> {
    return this.http.get<Page<Ticket>>('/api/tickets', { params: toParams(params) });
  }
  get(id: number): Observable<Ticket> {
    return this.http.get<Ticket>(`/api/tickets/${id}`);
  }
  create(body: unknown): Observable<Ticket> {
    return this.http.post<Ticket>('/api/tickets', body);
  }
  update(id: number, body: unknown): Observable<Ticket> {
    return this.http.patch<Ticket>(`/api/tickets/${id}`, body);
  }
  reply(id: number, content: string, internal: boolean): Observable<Ticket> {
    return this.http.post<Ticket>(`/api/tickets/${id}/messages`, { content, internal });
  }
}

@Injectable({ providedIn: 'root' })
export class DashboardApi {
  private http = inject(HttpClient);
  get(params: Params): Observable<Dashboard> {
    return this.http.get<Dashboard>('/api/dashboard', { params: toParams(params) });
  }
  objectives(year: number): Observable<ObjectiveRow[]> {
    return this.http.get<ObjectiveRow[]>('/api/objectives', { params: { year } });
  }
  saveObjectives(body: { userId: number; year: number; month: number; targetAmount: number }[]): Observable<ObjectiveRow[]> {
    return this.http.put<ObjectiveRow[]>('/api/objectives', body);
  }
}

@Injectable({ providedIn: 'root' })
export class PortalApi {
  private http = inject(HttpClient);
  home(): Observable<{ companyName: string; city: string | null; openQuotes: number; unpaidInvoices: number }> {
    return this.http.get<{ companyName: string; city: string | null; openQuotes: number; unpaidInvoices: number }>('/api/portal/home');
  }
  quotes(): Observable<Quote[]> {
    return this.http.get<Quote[]>('/api/portal/quotes');
  }
  decide(id: number, decision: 'accept' | 'refuse', comment?: string): Observable<Quote> {
    return this.http.post<Quote>(`/api/portal/quotes/${id}/${decision}`, { comment });
  }
  invoices(): Observable<Invoice[]> {
    return this.http.get<Invoice[]>('/api/portal/invoices');
  }
  tickets(page = 0): Observable<Page<Ticket>> {
    return this.http.get<Page<Ticket>>('/api/portal/tickets', { params: { page } });
  }
  ticket(id: number): Observable<Ticket> {
    return this.http.get<Ticket>(`/api/portal/tickets/${id}`);
  }
  openTicket(body: unknown): Observable<Ticket> {
    return this.http.post<Ticket>('/api/portal/tickets', body);
  }
  reply(id: number, content: string): Observable<Ticket> {
    return this.http.post<Ticket>(`/api/portal/tickets/${id}/messages`, { content, internal: false });
  }
}

@Injectable({ providedIn: 'root' })
export class PlatformApi {
  private http = inject(HttpClient);
  notifications(page = 0): Observable<Page<AppNotification>> {
    return this.http.get<Page<AppNotification>>('/api/notifications', { params: { page } });
  }
  unread(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>('/api/notifications/unread');
  }
  markRead(id: number): Observable<void> {
    return this.http.post<void>(`/api/notifications/${id}/read`, null);
  }
  markAllRead(): Observable<void> {
    return this.http.post<void>('/api/notifications/read-all', null);
  }
  settings(): Observable<Record<string, string>> {
    return this.http.get<Record<string, string>>('/api/settings');
  }
  saveSettings(values: Record<string, string>): Observable<Record<string, string>> {
    return this.http.put<Record<string, string>>('/api/settings', values);
  }
  runFollowUps(): Observable<{ created: number }> {
    return this.http.post<{ created: number }>('/api/automation/follow-ups/run', null);
  }
  runRetention(): Observable<{ anonymized: number }> {
    return this.http.post<{ anonymized: number }>('/api/privacy/retention/run', null);
  }
  draftEmail(body: { purpose: string; contactId?: number | null; opportunityId?: number | null; quoteId?: number | null; tone?: string; notes?: string }): Observable<EmailDraft> {
    return this.http.post<EmailDraft>('/api/assistant/draft', body);
  }
  exportPersonalData(contactId: number): Observable<Record<string, unknown>> {
    return this.http.get<Record<string, unknown>>(`/api/privacy/contacts/${contactId}/export`);
  }
  anonymizeContact(id: number): Observable<void> {
    return this.http.post<void>(`/api/privacy/contacts/${id}/anonymize`, null);
  }
  anonymizeLead(id: number): Observable<void> {
    return this.http.post<void>(`/api/privacy/leads/${id}/anonymize`, null);
  }
  invitePortal(contactId: number): Observable<User> {
    return this.http.post<User>(`/api/contacts/${contactId}/portal-access`, null);
  }
  updateRole(id: number, permissions: string[], label?: string): Observable<Role> {
    return this.http.put<Role>(`/api/roles/${id}`, { permissions, label });
  }
}
