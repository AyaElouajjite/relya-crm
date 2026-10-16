import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { QUOTE_STATUS_LABELS } from '../core/sales.models';
import { errorMessage, fieldErrors } from '../core/toast.service';
import { MadPipe, PillComponent, toDateInput } from './ui';

describe('Composants et utilitaires partagés', () => {
  it('formate les montants en dirhams', () => {
    const pipe = new MadPipe();
    expect(pipe.transform(12500.5).replace(/\s/g, ' ')).toBe('12 500,50 MAD');
    expect(pipe.transform(null)).toBe('—');
    expect(pipe.transform(1_250_000, true)).toContain('MAD');
  });

  it('affiche le libellé et la couleur du statut', () => {
    const fixture = TestBed.createComponent(PillComponent);
    fixture.componentRef.setInput('value', 'ACCEPTE');
    fixture.componentRef.setInput('labels', QUOTE_STATUS_LABELS);
    fixture.detectChanges();
    const span: HTMLElement = fixture.nativeElement.querySelector('span');
    expect(span.textContent?.trim()).toBe('Accepté');
    expect(span.className).toContain('emerald');
  });

  it('formate une date pour un champ de type date', () => {
    expect(toDateInput(new Date(2026, 0, 5))).toBe('2026-01-05');
    expect(toDateInput(null)).toBe('');
  });

  it('extrait les messages d’erreur de l’API', () => {
    const err = new HttpErrorResponse({ status: 400, error: { status: 400, message: 'Données invalides', errors: { ice: '15 chiffres' } } });
    expect(errorMessage(err)).toBe('15 chiffres');
    expect(fieldErrors(err)).toEqual({ ice: '15 chiffres' });
    expect(errorMessage(new HttpErrorResponse({ status: 0 }))).toContain('Serveur injoignable');
  });
});
