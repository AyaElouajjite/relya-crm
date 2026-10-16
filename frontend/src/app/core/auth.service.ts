import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { AuthResponse, Permission, User } from './models';

const TOKEN_KEY = 'crm.token';
const USER_KEY = 'crm.user';
const EXPIRES_KEY = 'crm.expiresAt';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);

  private readonly _user = signal<User | null>(this.restoreUser());
  readonly user = this._user.asReadonly();

  /** Méthode et non computed : l'expiration du jeton ne déclenche aucun signal. */
  isLoggedIn(): boolean {
    return this._user() !== null && this.token !== null;
  }

  get token(): string | null {
    const token = localStorage.getItem(TOKEN_KEY);
    const expiresAt = localStorage.getItem(EXPIRES_KEY);
    if (!token || !expiresAt || new Date(expiresAt) <= new Date()) {
      return null;
    }
    return token;
  }

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/login', { email, password }).pipe(tap((res) => this.storeSession(res)));
  }

  storeSession(res: AuthResponse): void {
    localStorage.setItem(TOKEN_KEY, res.token);
    localStorage.setItem(EXPIRES_KEY, res.expiresAt);
    this.setUser(res.user);
  }

  /** Déconnexion volontaire : le serveur révoque aussi le jeton (toutes les sessions du compte). */
  signOut(): void {
    if (this.token) {
      this.http.post('/api/auth/logout', null).subscribe({ error: () => {} });
    }
    this.logout();
  }

  /** Nettoyage local (jeton expiré ou révoqué). */
  logout(redirect = true): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(EXPIRES_KEY);
    localStorage.removeItem(USER_KEY);
    this._user.set(null);
    if (redirect) {
      this.router.navigate(['/login']);
    }
  }

  /** Recharge le profil depuis l'API (rôle ou permissions modifiés par un administrateur). */
  refreshMe(): Observable<User> {
    return this.http.get<User>('/api/auth/me').pipe(tap((u) => this.setUser(u)));
  }

  setUser(user: User): void {
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    this._user.set(user);
  }

  hasPermission(...permissions: Permission[]): boolean {
    const granted = this._user()?.permissions ?? [];
    return permissions.some((p) => granted.includes(p));
  }

  forgotPassword(email: string): Observable<void> {
    return this.http.post<void>('/api/auth/forgot-password', { email });
  }

  resetPassword(token: string, newPassword: string): Observable<void> {
    return this.http.post<void>('/api/auth/reset-password', { token, newPassword });
  }

  private restoreUser(): User | null {
    try {
      const raw = localStorage.getItem(USER_KEY);
      return raw && this.token ? (JSON.parse(raw) as User) : null;
    } catch {
      return null;
    }
  }
}
