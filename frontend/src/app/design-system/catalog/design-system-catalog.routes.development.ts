import { Routes } from '@angular/router';

export const designSystemCatalogRoutes: Routes = [
  {
    path: '__design-system',
    data: { fullscreen: true },
    loadComponent: () =>
      import('./design-system-catalog.component').then((m) => m.DesignSystemCatalogComponent),
    title: 'Design System Nexo'
  },
  {
    path: '__design-system/analitico',
    data: { fullscreen: true },
    loadComponent: () =>
      import('./design-system-catalog-analytics.component').then(
        (m) => m.DesignSystemCatalogAnalyticsComponent
      ),
    title: 'Design System Nexo · Analítico'
  }
];
