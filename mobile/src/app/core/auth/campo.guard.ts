import { inject } from '@angular/core';
import { Router, CanMatchFn } from '@angular/router';
import { AuthService } from './auth.service';

export const campoGuard: CanMatchFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isCampo()) {
    return true;
  }

  return router.createUrlTree(['/dashboard']);
};
