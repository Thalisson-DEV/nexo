import { Routes } from '@angular/router';

export const TRANSFERENCIAS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./list/transferencias-list.component').then(m => m.TransferenciasListComponent)
  },
  {
    path: 'nova',
    loadComponent: () => import('./nova/nova-transferencia.component').then(m => m.NovaTransferenciaComponent)
  },
  {
    path: ':id',
    loadComponent: () => import('./detalhe/transferencia-detalhe.component').then(m => m.TransferenciaDetalheComponent)
  }
];
