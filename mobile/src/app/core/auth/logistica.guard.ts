import { inject } from '@angular/core';
import { Router, CanMatchFn } from '@angular/router';
import { AuthService } from './auth.service';

export const logisticaGuard: CanMatchFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.podeAcessarLogistica()) {
    return true;
  }

  return router.createUrlTree(['/dashboard']);
};
