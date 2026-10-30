import { Routes } from '@angular/router';

export const INSPECOES_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./inspecoes-list.component').then(m => m.InspecoesListComponent)
  },
  {
    path: 'nova',
    loadComponent: () => import('./nova/nova-inspecao.component').then(m => m.NovaInspecaoComponent)
  },
  {
    path: ':id',
    loadComponent: () => import('./detalhe/inspecao-detalhe.component').then(m => m.InspecaoDetalheComponent)
  }
];
