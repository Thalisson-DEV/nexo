import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { ConnectivityService } from '../sync/connectivity.service';

export const offlineInterceptor: HttpInterceptorFn = (req, next) => {
  const connectivityService = inject(ConnectivityService);

  // Se o aplicativo estiver operando em modo offline (corte no 4G ou manual),
  // interrompe de imediato sem esperar timeout de rede em rota bloqueada
  if (!connectivityService.isOnline()) {
    return throwError(() => new HttpErrorResponse({
      status: 0,
      statusText: 'Offline',
      url: req.url,
      error: { message: 'Aplicação operando em modo offline' }
    }));
  }

  return next(req).pipe(
    catchError((error) => {
      // Status 0 indica erro de rede ou CORS, 504 indica Gateway Timeout (comum em conexões móveis instáveis)
      if (error.status === 0 || error.status === 504 || error.status === 408 || !connectivityService.isOnline()) {
        console.warn(`Falha de rede/timeout (${error.status}) detectada no interceptor offline para:`, req.url);
      }
      return throwError(() => error);
    })
  );
};
