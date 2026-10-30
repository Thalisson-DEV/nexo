import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { loginGuard } from './core/auth/login.guard';
import { adminGuard } from './core/auth/admin.guard';
import { desviosGuard } from './core/auth/desvios.guard';
import { logisticaGuard } from './core/auth/logistica.guard';
import { campoGuard } from './core/auth/campo.guard';
import { almoxGuard } from './core/auth/almox.guard';
import { retomadaGuard } from './core/sessao/retomada.guard';

export const routes: Routes = [
  {
    // Raiz = start_url do manifest (abrir pelo ícone): decide entre voltar ao ponto
    // exato, pedir login e depois voltar, ou abrir o início. Nunca renderiza nada.
    path: '',
    pathMatch: 'full',
    canActivate: [retomadaGuard],
    children: []
  },
  {
    path: 'login',
    canActivate: [loginGuard],
    loadComponent: () => import('./features/auth/login.component').then(m => m.LoginComponent)
  },
  {
    path: 'sync',
    canActivate: [authGuard],
    loadComponent: () => import('./features/sync/sync-loading.component').then(m => m.SyncLoadingComponent)
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./features/shell/app-shell.component').then(m => m.AppShellComponent),
    children: [
      {
        path: 'hoje',
        loadComponent: () => import('./features/hoje/hoje.component').then(m => m.HojeComponent)
      },
      {
        // Rota da versão anterior: links, atalhos e retratos de sessão antigos continuam válidos.
        path: 'dashboard',
        redirectTo: 'hoje'
      },
      {
        path: 'material',
        canMatch: [logisticaGuard],
        loadComponent: () => import('./features/material/material.component').then(m => m.MaterialComponent)
      },
      {
        path: 'solicitacoes',
        canMatch: [logisticaGuard],
        loadChildren: () => import('./features/solicitacoes/solicitacoes.routes').then(m => m.SOLICITACOES_ROUTES)
      },
      {
        path: 'transferencias',
        canMatch: [logisticaGuard],
        loadChildren: () => import('./features/transferencias/transferencias.routes').then(m => m.TRANSFERENCIAS_ROUTES)
      },
      {
        path: 'turno/abrir',
        canMatch: [campoGuard],
        loadComponent: () => import('./features/turno/abrir-turno.component').then(m => m.AbrirTurnoComponent)
      },
      {
        path: 'campo',
        canMatch: [campoGuard],
        loadChildren: () => import('./features/campo/campo.routes').then(m => m.CAMPO_ROUTES)
      },
      {
        // Módulo administrativo: fora do escopo do encarregado de campo.
        path: 'inspecoes',
        canMatch: [adminGuard],
        loadChildren: () => import('./features/inspecoes/inspecoes.routes').then(m => m.INSPECOES_ROUTES)
      },
      {
        // Almoxarifado do perfil administrativo: gestão de solicitações e movimentações de estoque.
        path: 'almox',
        canMatch: [almoxGuard],
        loadChildren: () => import('./features/almox/almox.routes').then(m => m.ALMOX_ROUTES)
      },
      {
        path: 'desvios',
        canMatch: [desviosGuard],
        loadComponent: () => import('./features/desvios/meus-desvios.component').then(m => m.MeusDesviosComponent)
      },
      {
        path: 'perfil',
        loadComponent: () => import('./features/perfil/perfil.component').then(m => m.PerfilComponent)
      },
      {
        path: '',
        redirectTo: 'hoje',
        pathMatch: 'full'
      }
    ]
  },
  {
    path: '**',
    redirectTo: 'hoje'
  }
];
