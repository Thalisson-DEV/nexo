import { Routes } from '@angular/router';

export const SOLICITACOES_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./list/solicitacoes-list.component').then(m => m.SolicitacoesListComponent)
  },
  {
    path: 'nova',
    loadComponent: () => import('./nova/nova-solicitacao.component').then(m => m.NovaSolicitacaoComponent)
  },
  {
    path: 'estoque',
    loadComponent: () => import('./estoque/estoque-list.component').then(m => m.EstoqueListComponent)
  },
  {
    path: ':id',
    loadComponent: () => import('./detalhe/solicitacao-detalhe.component').then(m => m.SolicitacaoDetalheComponent)
  }
];
