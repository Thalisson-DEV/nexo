import { Routes } from '@angular/router';

export const CAMPO_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./list/execucoes-list.component').then(m => m.ExecucoesListComponent)
  },
  {
    path: 'nova',
    loadComponent: () => import('./nova/nova-execucao.component').then(m => m.NovaExecucaoComponent)
  },
  {
    path: ':id',
    loadComponent: () => import('./detalhe/execucao-detalhe.component').then(m => m.ExecucaoDetalheComponent)
  }
];
