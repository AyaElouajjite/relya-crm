import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import {
  AuditLog,
  AuthResponse,
  Company,
  Company360,
  CompanyRequest,
  Contact,
  ContactRequest,
  ImportResult,
  LoginLog,
  Page,
  Role,
  User,
  UserSummary
} from './models';

type Params = Record<string, string | number | boolean | null | undefined>;

function toParams(params: Params): HttpParams {
  let p = new HttpParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== null && value !== undefined && value !== '') {
      p = p.set(key, String(value));
    }
  }
  return p;
}

/** Déclenche le téléchargement d'un fichier renvoyé par l'API. */
function saveDownload(res: HttpResponse<Blob>, fallbackName: string): void {
  const disposition = res.headers.get('Content-Disposition') ?? '';
  const match = /filename="?([^";]+)"?/.exec(disposition);
  const url = URL.createObjectURL(res.body!);
  const a = document.createElement('a');
  a.href = url;
  a.download = match?.[1] ?? fallbackName;
  a.click();
  URL.revokeObjectURL(url);
}

@Injectable({ providedIn: 'root' })
export class CompanyApi {
  private http = inject(HttpClient);

  search(params: Params): Observable<Page<Company>> {
    return this.http.get<Page<Company>>('/api/companies', { params: toParams(params) });
  }
  filters(): Observable<{ sectors: string[]; cities: string[] }> {
    return this.http.get<{ sectors: string[]; cities: string[] }>('/api/companies/filters');
  }
  get(id: number): Observable<Company360> {
    return this.http.get<Company360>(`/api/companies/${id}`);
  }
  create(body: CompanyRequest): Observable<Company> {
    return this.http.post<Company>('/api/companies', body);
  }
  update(id: number, body: CompanyRequest): Observable<Company> {
    return this.http.put<Company>(`/api/companies/${id}`, body);
  }
  setArchived(id: number, value: boolean): Observable<void> {
    return this.http.patch<void>(`/api/companies/${id}/archived`, null, { params: { value } });
  }
  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/api/companies/${id}`);
  }
  export(params: Params): Observable<void> {
    return this.http
      .get('/api/companies/export', { params: toParams(params), observe: 'response', responseType: 'blob' })
      .pipe(map((res) => saveDownload(res, 'entreprises.csv')));
  }
  import(file: File): Observable<ImportResult> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ImportResult>('/api/companies/import', form);
  }
}

@Injectable({ providedIn: 'root' })
export class ContactApi {
  private http = inject(HttpClient);

  search(params: Params): Observable<Page<Contact>> {
    return this.http.get<Page<Contact>>('/api/contacts', { params: toParams(params) });
  }
  get(id: number): Observable<Contact> {
    return this.http.get<Contact>(`/api/contacts/${id}`);
  }
  create(body: ContactRequest): Observable<Contact> {
    return this.http.post<Contact>('/api/contacts', body);
  }
  update(id: number, body: ContactRequest): Observable<Contact> {
    return this.http.put<Contact>(`/api/contacts/${id}`, body);
  }
  setArchived(id: number, value: boolean): Observable<void> {
    return this.http.patch<void>(`/api/contacts/${id}/archived`, null, { params: { value } });
  }
  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/api/contacts/${id}`);
  }
  export(params: Params): Observable<void> {
    return this.http
      .get('/api/contacts/export', { params: toParams(params), observe: 'response', responseType: 'blob' })
      .pipe(map((res) => saveDownload(res, 'contacts.csv')));
  }
  import(file: File): Observable<ImportResult> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ImportResult>('/api/contacts/import', form);
  }
}

@Injectable({ providedIn: 'root' })
export class UserApi {
  private http = inject(HttpClient);

  search(params: Params): Observable<Page<User>> {
    return this.http.get<Page<User>>('/api/users', { params: toParams(params) });
  }
  assignable(): Observable<UserSummary[]> {
    return this.http.get<UserSummary[]>('/api/users/assignable');
  }
  agents(): Observable<UserSummary[]> {
    return this.http.get<UserSummary[]>('/api/users/agents');
  }
  roles(): Observable<Role[]> {
    return this.http.get<Role[]>('/api/roles');
  }
  create(body: unknown): Observable<User> {
    return this.http.post<User>('/api/users', body);
  }
  update(id: number, body: unknown): Observable<User> {
    return this.http.put<User>(`/api/users/${id}`, body);
  }
  setActive(id: number, value: boolean): Observable<User> {
    return this.http.patch<User>(`/api/users/${id}/active`, null, { params: { value } });
  }
  updateProfile(body: { firstName: string; lastName: string; phone: string | null }): Observable<User> {
    return this.http.put<User>('/api/auth/me', body);
  }
  changePassword(currentPassword: string, newPassword: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/me/password', { currentPassword, newPassword });
  }
  myLogins(page = 0): Observable<Page<LoginLog>> {
    return this.http.get<Page<LoginLog>>('/api/auth/me/logins', { params: { page } });
  }
  allLogins(params: Params): Observable<Page<LoginLog>> {
    return this.http.get<Page<LoginLog>>('/api/admin/logins', { params: toParams(params) });
  }
  audit(page = 0): Observable<Page<AuditLog>> {
    return this.http.get<Page<AuditLog>>('/api/admin/audit', { params: { page } });
  }
}
