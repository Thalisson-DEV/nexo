import { inject } from '@angular/core';
import { Router, CanActivateFn } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { MessageService } from 'primeng/api';

export const authGuard: CanActivateFn = (route) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const messageService = inject(MessageService);

  // 1. Verifica se está autenticado
  if (!authService.isAuthenticated()) {
    return router.parseUrl('/login');
  }

  // 2. Verifica se a rota exige uma permissão específica (Authority)
  const requiredPermission = route.data?.['permission'] as string;
  
  if (requiredPermission) {
    const user = authService.currentUser();
    const hasPermission = user?.permissoes?.includes(requiredPermission);

    if (!hasPermission) {
      messageService.add({
        severity: 'warn',
        summary: 'Acesso Restrito',
        detail: 'Você não tem permissão para acessar esta funcionalidade.',
        life: 4000
      });
      // Redireciona para home se não tiver permissão, mas mantém o login
      return router.parseUrl('/home');
    }
  }

  return true;
};
