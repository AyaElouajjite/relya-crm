import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { User } from './models';

const user = (permissions: string[]): User =>
  ({ id: 1, firstName: 'Salma', lastName: 'Idrissi', fullName: 'Salma Idrissi', email: 's@crm.ma', phone: null, active: true,
    roleCode: 'COMMERCIAL', roleLabel: 'Commercial', permissions, lastLoginAt: null, createdAt: '' }) as User;

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('stocke la session à la connexion et expose les permissions', () => {
    auth.login('s@crm.ma', 'secret').subscribe();
    const expiresAt = new Date(Date.now() + 3600_000).toISOString();
    http.expectOne('/api/auth/login').flush({ token: 'jwt', expiresAt, user: user(['CONTACT_READ', 'LEAD_MANAGE']) });

    expect(auth.isLoggedIn()).toBeTrue();
    expect(auth.token).toBe('jwt');
    expect(auth.hasPermission('LEAD_MANAGE')).toBeTrue();
    expect(auth.hasPermission('USER_MANAGE')).toBeFalse();
    expect(auth.hasPermission('USER_MANAGE', 'CONTACT_READ')).toBeTrue();
  });

  it('considère un jeton expiré comme une déconnexion', () => {
    auth.storeSession({ token: 'old', expiresAt: new Date(Date.now() - 1000).toISOString(), user: user([]) });
    expect(auth.token).toBeNull();
    expect(auth.isLoggedIn()).toBeFalse();
  });

  it('révoque le jeton côté serveur à la déconnexion', () => {
    auth.storeSession({ token: 'jwt', expiresAt: new Date(Date.now() + 3600_000).toISOString(), user: user([]) });
    auth.signOut();
    http.expectOne({ method: 'POST', url: '/api/auth/logout' }).flush(null);
    expect(auth.isLoggedIn()).toBeFalse();
    expect(localStorage.getItem('crm.token')).toBeNull();
  });
});
