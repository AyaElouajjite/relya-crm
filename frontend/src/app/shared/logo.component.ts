import { Component, computed, input } from '@angular/core';
import { BRAND } from '../core/brand';

/**
 * Logo Relya : un symbole (courbe de croissance reliant des contacts) et le nom.
 * - variant « dark » : pour fond clair ; « light » : pour fond sombre.
 */
@Component({
  selector: 'app-logo',
  standalone: true,
  template: `
    <span class="inline-flex items-center gap-2.5" [attr.aria-label]="name">
      <svg [attr.width]="size()" [attr.height]="size()" viewBox="0 0 40 40" fill="none" aria-hidden="true">
        <defs>
          <linearGradient [attr.id]="gradientId" x1="4" y1="2" x2="36" y2="38" gradientUnits="userSpaceOnUse">
            <stop stop-color="#3B82F6" />
            <stop offset="1" stop-color="#4338CA" />
          </linearGradient>
        </defs>
        <rect width="40" height="40" rx="11" [attr.fill]="'url(#' + gradientId + ')'" />
        <path d="M10 27.5 17 20.5l5 4 8.5-10" stroke="#fff" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" opacity=".95" />
        <circle cx="10" cy="27.5" r="2.6" fill="#fff" />
        <circle cx="17" cy="20.5" r="2.6" fill="#fff" />
        <circle cx="22" cy="24.5" r="2.6" fill="#fff" />
        <circle cx="30.5" cy="14.5" r="3.6" fill="#fff" />
        <circle cx="30.5" cy="14.5" r="1.4" fill="#4338CA" />
      </svg>
      @if (showText()) {
        <span class="leading-none">
          <span class="block font-semibold tracking-tight" [class]="textClass()" [style.font-size.px]="size() * 0.56">{{ name }}</span>
          @if (showProduct()) {
            <span class="mt-0.5 block text-[10px] font-medium uppercase tracking-[0.18em]" [class]="subClass()">{{ product }}</span>
          }
        </span>
      }
    </span>
  `
})
export class LogoComponent {
  size = input(32);
  variant = input<'dark' | 'light'>('dark');
  showText = input(true);
  showProduct = input(false);
  name = BRAND.name;
  product = BRAND.product;
  gradientId = 'relya-grad-' + Math.random().toString(36).slice(2, 8);
  textClass = computed(() => (this.variant() === 'light' ? 'text-white' : 'text-slate-900'));
  subClass = computed(() => (this.variant() === 'light' ? 'text-white/60' : 'text-slate-400'));
}
