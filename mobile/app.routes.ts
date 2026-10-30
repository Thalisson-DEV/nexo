import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
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
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent)
      },
      {
        path: 'solicitacoes',
        loadChildren: () => import('./features/solicitacoes/solicitacoes.routes').then(m => m.SOLICITACOES_ROUTES)
      },
      {
        path: 'campo',
        loadComponent: () => import('./features/placeholder.component').then(m => m.PlaceholderComponent)
      },
      {
        path: 'perfil',
        loadComponent: () => import('./features/placeholder.component').then(m => m.PlaceholderComponent)
      },
      {
        path: '',
        redirectTo: 'dashboard',
        pathMatch: 'full'
      }
    ]
  },
  {
    path: '**',
    redirectTo: 'dashboard'
  }
];
