import { Component, input, output, model, inject, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PanelMenuModule } from 'primeng/panelmenu';
import { ButtonModule } from 'primeng/button';
import { RippleModule } from 'primeng/ripple';
import { BadgeModule } from 'primeng/badge';
import { TooltipModule } from 'primeng/tooltip';
import { MenuItem } from 'primeng/api';
import { RouterLink, Router } from '@angular/router';
import { ViewStateStore, linkToUrl } from '../../../core/navigation/view-state.store';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [
    CommonModule,
    PanelMenuModule,
    ButtonModule,
    RippleModule,
    BadgeModule,
    TooltipModule,
    RouterLink
  ],
  template: `
    <aside
      class="fixed inset-y-0 left-0 z-50 flex flex-col bg-[var(--ces-surface)] text-[var(--ces-foreground)] border-r border-[var(--ces-border)] transition-all duration-180 ease-in-out lg:relative lg:z-40 h-full"
      [class.w-90]="menuVisible() || isMobile()"
      [class.lg:w-20]="!menuVisible() && !isMobile()"
      [class.max-lg:-translate-x-full]="!menuVisible() && isMobile()"
    >
      <!-- Sidebar Content -->
      <div class="flex-grow overflow-y-auto overflow-x-hidden custom-scrollbar">

        <!-- Toggle Button (Aligned with Menu Items, Only on Desktop) -->
        @if (!isMobile()) {
          <div class="px-3 mt-3 mb-2 animate-in fade-in duration-300">
            <p-button
              icon="pi pi-bars"
              [text]="true"
              (onClick)="toggleMenu.emit()"
              [styleClass]="menuVisible()
                ? '!text-[var(--ces-foreground-muted)] hover:!bg-[var(--ces-brand-soft)] hover:!text-[var(--ces-brand)] !w-full !h-11 !p-0 !rounded-xl !flex !justify-center !items-center !border-0 transition-all'
                : '!text-[var(--ces-foreground-muted)] hover:!bg-[var(--ces-brand-soft)] hover:!text-[var(--ces-brand)] !w-full !h-12 !p-0 !rounded-xl !flex !justify-center !items-center !border-0 transition-all'"
            ></p-button>
          </div>

          <!-- Aligned Separator -->
          <div class="px-6 mb-4">
            <div class="h-px bg-[var(--ces-border)] w-full"></div>
          </div>
        }

        @if (menuVisible() || isMobile()) {
          <!-- Expanded Menu -->
          <div class="px-3 animate-in slide-in-from-left-2 duration-300" [class.mt-4]="isMobile()">
            @if (copilotoItem(); as item) {
              <a pRipple
                 [routerLink]="item.routerLink"
                 class="flex items-center px-3.5 py-2.5 my-0.5 cursor-pointer group rounded-xl transition-all duration-150 text-[var(--ces-foreground-muted)] hover:bg-[var(--ces-surface-muted)] hover:text-[var(--ces-foreground)] ces-sidebar-destaque"
                 [class.ces-sidebar-active-link]="isItemActive(item)"
                 (click)="onItemClick(item)">
                <i [class]="item.icon + ' text-[var(--ces-foreground-subtle)] group-hover:text-[var(--ces-brand)] transition-colors'" [class.!text-[var(--ces-brand)]]="isItemActive(item)"></i>
                <span class="ms-3 text-[11px] font-medium">{{ item.label }}</span>
                @if (item.badge) { <span class="ces-sidebar-badge">{{ item.badge }}</span> }
              </a>
            }
            <p-panelmenu [model]="panelItems()" class="!border-0 !w-full custom-panelmenu">
              <ng-template #item let-item>
                @if (item.routerLink) {
                  <a pRipple
                     [routerLink]="item.routerLink"
                     [queryParams]="item.queryParams"
                     class="flex items-center px-3.5 py-2.5 my-0.5 cursor-pointer group rounded-xl transition-all duration-150 text-[var(--ces-foreground-muted)] hover:bg-[var(--ces-surface-muted)] hover:text-[var(--ces-foreground)]"
                     [class.ces-sidebar-active-link]="isItemActive(item)"
                     [class.ces-sidebar-destaque]="item.styleClass === 'ces-sidebar-destaque'"
                     (click)="onItemClick(item)">
                    <i [class]="item.icon + ' text-[var(--ces-foreground-subtle)] group-hover:text-[var(--ces-brand)] transition-colors'" [class.!text-[var(--ces-brand)]]="isItemActive(item)"></i>
                    <span class="ms-3 text-[11px] font-medium text-[var(--ces-foreground-muted)] group-hover:text-[var(--ces-foreground)]" [class.!text-[var(--ces-brand)]]="isItemActive(item)" [class.!font-bold]="isItemActive(item)">{{ item.label }}</span>
                    @if (item.badge) { <span class="ces-sidebar-badge">{{ item.badge }}</span> }
                  </a>
                } @else if (item.url) {
                  <a pRipple
                     [href]="item.url"
                     [target]="item.target || '_self'"
                     class="flex items-center px-3.5 py-2.5 my-0.5 cursor-pointer group rounded-xl transition-all duration-150 text-[var(--ces-foreground-muted)] hover:bg-[var(--ces-surface-muted)] hover:text-[var(--ces-foreground)]"
                     (click)="onItemClick(item)">
                    <i [class]="item.icon + ' text-[var(--ces-foreground-subtle)] group-hover:text-[var(--ces-brand)] transition-colors'"></i>
                    <span class="ms-3 text-[11px] font-medium text-[var(--ces-foreground-muted)] group-hover:text-[var(--ces-foreground)]">{{ item.label }}</span>
                  </a>
                } @else {
                  <a pRipple
                     class="flex items-center px-3.5 py-2.5 my-0.5 cursor-pointer group rounded-xl transition-all duration-150 text-[var(--ces-foreground-muted)] hover:bg-[var(--ces-surface-muted)] hover:text-[var(--ces-foreground)]"
                     (click)="onItemClick(item)">
                    <i [class]="item.icon + ' text-[var(--ces-foreground-subtle)] group-hover:text-[var(--ces-brand)] transition-colors'"></i>
                    <span class="ms-3 text-[11px] font-medium text-[var(--ces-foreground-muted)] group-hover:text-[var(--ces-foreground)]">{{ item.label }}</span>
                    @if (item.items && item.items.length > 0) {
                      <i class="pi pi-chevron-down ms-auto text-[10px] text-[var(--ces-foreground-subtle)] group-hover:text-[var(--ces-brand)] transition-transform"
                        [class.rotate-180]="item.expanded"></i>
                    }
                  </a>
                }
              </ng-template>
            </p-panelmenu>
          </div>
        } @else {
          <!-- Collapsed Menu (Just Icons) - Only on Desktop -->
          <div class="flex flex-col items-center gap-2 mt-2 animate-in fade-in duration-300">
            @for (item of menuItems(); track item.label) {
              @if (item.visible !== false) {
              <div class="relative w-12 h-12">
                <p-button
                  [icon]="item.icon"
                  [text]="true"
                  [pTooltip]="item.label"
                  tooltipPosition="right"
                  (onClick)="onExpandAndFocus(item)"
                  [styleClass]="'!w-12 !h-12 !p-0 !rounded-xl !text-xl transition-all ' +
                    (isItemActive(item)
                      ? '!bg-[var(--ces-brand-soft)] !text-[var(--ces-brand)]'
                      : '!text-[var(--ces-foreground-muted)] hover:!bg-[var(--ces-surface-muted)] hover:!text-[var(--ces-foreground)]')"
                ></p-button>
              </div>
              }
            }
          </div>
        }
      </div>
    </aside>
  `
})
export class SidebarComponent {
  private readonly router = inject(Router);
  private readonly viewState = inject(ViewStateStore);

  isMobile = input<boolean>(false);
  menuVisible = input<boolean>(false);
  menuItems = model<MenuItem[]>([]);
  protected readonly copilotoItem = computed(() => this.menuItems().find((item) => item.routerLink === '/copiloto' && item.visible !== false));
  protected readonly panelItems = computed(() => this.menuItems().filter((item) => item.routerLink !== '/copiloto'));

  toggleMenu = output<void>();
  expandAndFocus = output<MenuItem>();

  onItemClick(item: MenuItem) {
    // Navegação intencional pelo menu abre a tela limpa (sem rascunho/estado antigo).
    if (item.routerLink) this.viewState.clearRoute(linkToUrl(item.routerLink));
    if (this.isMobile() && item.routerLink) {
      this.toggleMenu.emit();
    }
  }

  onExpandAndFocus(item: MenuItem) {
    if (item.routerLink && !item.items?.length) {
      this.viewState.clearRoute(linkToUrl(item.routerLink));
      void this.router.navigateByUrl(Array.isArray(item.routerLink) ? item.routerLink.join('/') : String(item.routerLink));
      return;
    }
    this.expandAndFocus.emit(item);
  }

  isItemActive(item: MenuItem): boolean {
    if (item.routerLink) {
      const currentUrl = this.router.url;
      const currentUrlTree = this.router.parseUrl(currentUrl);

      const currentPath = currentUrl.split('?')[0];

      const itemLink = Array.isArray(item.routerLink)
        ? item.routerLink.join('/')
        : item.routerLink;
      const itemPath = itemLink.split('?')[0];

      const isExact = item.routerLinkActiveOptions?.exact === true;
      const pathMatches = isExact
        ? currentPath === itemPath
        : (currentPath === itemPath || currentPath.startsWith(itemPath + '/'));
      if (!pathMatches) {
        return false;
      }

      const currentParams = currentUrlTree.queryParams || {};
      const itemParams = item.queryParams || {};
      const itemParamKeys = Object.keys(itemParams);

      if (itemParamKeys.length > 0) {
        return itemParamKeys.every(
          (key) => String(currentParams[key]) === String(itemParams[key])
        );
      }

      if (Object.keys(currentParams).length > 0) {
        return false;
      }

      // Se este item deu match apenas por prefixo (não exato), mas existe no menu outro item
      // com match mais específico (ou exato) para a rota atual, este item não deve ficar ativo.
      if (currentPath !== itemPath && this.hasMoreSpecificMatch(currentPath, itemPath)) {
        return false;
      }

      return true;
    }

    if (item.items && item.items.length > 0) {
      return item.items.some((child) => this.isItemActive(child));
    }

    return false;
  }

  private hasMoreSpecificMatch(currentPath: string, currentItemPath: string): boolean {
    const allLinks = this.collectAllRouterLinks(this.menuItems());
    return allLinks.some((link) => {
      if (link === currentItemPath) return false;
      if (link === currentPath) return true;
      if (currentPath.startsWith(link + '/') && link.length > currentItemPath.length) return true;
      return false;
    });
  }

  private collectAllRouterLinks(items: MenuItem[]): string[] {
    const links: string[] = [];
    for (const item of items) {
      if (item.routerLink) {
        const link = Array.isArray(item.routerLink) ? item.routerLink.join('/') : item.routerLink;
        links.push(link.split('?')[0]);
      }
      if (item.items && item.items.length > 0) {
        links.push(...this.collectAllRouterLinks(item.items));
      }
    }
    return links;
  }
}
