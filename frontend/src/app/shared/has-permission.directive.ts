import { Directive, TemplateRef, ViewContainerRef, effect, inject, input } from '@angular/core';
import { AuthService } from '../core/auth.service';
import { Permission } from '../core/models';

/** Affiche l'élément seulement si l'utilisateur a au moins une des permissions : *appCan="'CONTACT_WRITE'" */
@Directive({ selector: '[appCan]', standalone: true })
export class HasPermissionDirective {
  private auth = inject(AuthService);
  private tpl = inject(TemplateRef<unknown>);
  private vcr = inject(ViewContainerRef);

  appCan = input.required<Permission | Permission[]>();
  private shown = false;

  constructor() {
    effect(() => {
      const value = this.appCan();
      this.auth.user(); // réévalue si l'utilisateur change
      const allowed = this.auth.hasPermission(...(Array.isArray(value) ? value : [value]));
      if (allowed && !this.shown) {
        this.vcr.createEmbeddedView(this.tpl);
        this.shown = true;
      } else if (!allowed && this.shown) {
        this.vcr.clear();
        this.shown = false;
      }
    });
  }
}
