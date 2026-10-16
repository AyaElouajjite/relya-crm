import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { Permission } from './models';

export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  if (auth.isLoggedIn()) {
    return true;
  }
  return inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

export const guestGuard: CanActivateFn = () =>
  inject(AuthService).isLoggedIn() ? inject(Router).createUrlTree(['/']) : true;

/** Les comptes « Client (portail) » n'accèdent qu'à l'espace client. */
export const staffGuard: CanActivateFn = () =>
  inject(AuthService).hasPermission('PORTAL_ACCESS') ? inject(Router).createUrlTree(['/portal']) : true;

export const portalGuard: CanActivateFn = () =>
  inject(AuthService).hasPermission('PORTAL_ACCESS') ? true : inject(Router).createUrlTree(['/']);

/** Usage : canActivate: [permissionGuard('USER_MANAGE')] */
export const permissionGuard =
  (...permissions: Permission[]): CanActivateFn =>
  () =>
    inject(AuthService).hasPermission(...permissions) ? true : inject(Router).createUrlTree(['/']);
