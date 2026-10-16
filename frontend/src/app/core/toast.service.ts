import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { ApiError } from './models';

export interface Toast {
  id: number;
  type: 'success' | 'error' | 'info';
  message: string;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  private nextId = 1;
  readonly toasts = signal<Toast[]>([]);

  success(message: string): void {
    this.push('success', message);
  }

  info(message: string): void {
    this.push('info', message);
  }

  error(err: unknown, fallback = 'Une erreur est survenue'): void {
    this.push('error', errorMessage(err, fallback));
  }

  dismiss(id: number): void {
    this.toasts.update((list) => list.filter((t) => t.id !== id));
  }

  private push(type: Toast['type'], message: string): void {
    const id = this.nextId++;
    this.toasts.update((list) => [...list, { id, type, message }]);
    setTimeout(() => this.dismiss(id), type === 'error' ? 6000 : 3500);
  }
}

export function errorMessage(err: unknown, fallback = 'Une erreur est survenue'): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) {
      return 'Serveur injoignable. Vérifiez votre connexion.';
    }
    const body = err.error as ApiError | null;
    if (body?.errors) {
      return Object.values(body.errors).join(' · ');
    }
    return body?.message ?? fallback;
  }
  return fallback;
}

/** Erreurs de validation champ par champ renvoyées par l'API. */
export function fieldErrors(err: unknown): Record<string, string> {
  return err instanceof HttpErrorResponse ? ((err.error as ApiError | null)?.errors ?? {}) : {};
}
