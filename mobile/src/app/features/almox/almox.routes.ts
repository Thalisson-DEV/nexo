import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import {
  almoxEntregaGuard, almoxMovimentacoesGuard, almoxMovimentarGuard, almoxSolicitacoesGuard,
} from '../../core/auth/almox.guard';

export const ALMOX_ROUTES: Routes = [
  {
    path: '',
    pathMatch: 'full',
    // Abre na parte que a pessoa pode usar (solicitações primeiro).
    redirectTo: () => (inject(AuthService).podeVerSolicitacoesAlmox() ? '/almox/solicitacoes' : '/almox/movimentacoes'),
  },
  {
    path: 'solicitacoes',
    canMatch: [almoxSolicitacoesGuard],
    children: [
      { path: '', loadComponent: () => import('./solicitacoes/solicitacoes-almox.component').then(m => m.SolicitacoesAlmoxComponent) },
      {
        path: ':id/entrega',
        canMatch: [almoxEntregaGuard],
        loadComponent: () => import('./solicitacoes/entrega-almox.component').then(m => m.EntregaAlmoxComponent),
      },
      { path: ':id', loadComponent: () => import('./solicitacoes/solicitacao-almox-detalhe.component').then(m => m.SolicitacaoAlmoxDetalheComponent) },
    ],
  },
  {
    path: 'movimentacoes',
    canMatch: [almoxMovimentacoesGuard],
    children: [
      { path: '', loadComponent: () => import('./movimentacoes/movimentacoes-almox.component').then(m => m.MovimentacoesAlmoxComponent) },
      {
        path: 'nova/:tipo',
        canMatch: [almoxMovimentarGuard],
        loadComponent: () => import('./movimentacoes/nova-movimentacao.component').then(m => m.NovaMovimentacaoComponent),
      },
      { path: ':id', loadComponent: () => import('./movimentacoes/movimentacao-almox-detalhe.component').then(m => m.MovimentacaoAlmoxDetalheComponent) },
    ],
  },
  { path: '**', redirectTo: '/almox' },
];
