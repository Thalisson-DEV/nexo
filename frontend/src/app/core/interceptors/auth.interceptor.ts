import { HttpErrorResponse, HttpEvent, HttpHandlerFn, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { Observable, catchError, from, switchMap, throwError } from 'rxjs';
import { MessageService } from 'primeng/api';
import { classificar401 } from '../models/auth.models';

/** Login, renovação e logout se autenticam pelo cookie da sessão, não pelo Bearer. */
const ROTAS_DE_SESSAO = /\/v1\/auth\/(login|refresh|logout)$/;

/**
 * Anexa o token de acesso e resolve o 401 sem nunca derrubar a sessão local.
 *
 * - token vencido: renova antes de enviar (quando dá para saber pelo `exp`);
 * - 401 de token (`TOKEN_EXPIRADO`/`TOKEN_INVALIDO`): renova uma vez e repete a requisição;
 * - 401 `SESSAO_INVALIDA`: marca a sessão para novo login; o app segue funcionando offline;
 * - falha da renovação (sem rede, 5xx): devolve o 401 original para quem chamou, que
 *   decide (a fila de sincronização guarda o item e tenta depois).
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const messageService = inject(MessageService);

  if (ROTAS_DE_SESSAO.test(req.url)) {
    return next(req.clone({ setHeaders: { 'X-Requested-With': 'XMLHttpRequest' }, withCredentials: true }));
  }

  const tratar = (token: string | null, podeRenovar: boolean): Observable<HttpEvent<unknown>> =>
    enviar(next, req, token).pipe(
      catchError((error: unknown) => {
        if (!(error instanceof HttpErrorResponse) || error.status !== 401) {
          return throwError(() => error);
        }

        switch (classificar401(error.error)) {
          case 'acesso-negado':
            messageService.add({
              severity: 'error',
              summary: 'Acesso Negado',
              detail: error.error?.detail || 'Você não tem permissão para realizar esta ação.',
              life: 5000,
            });
            return throwError(() => error);
          case 'sessao-invalida':
            authService.marcarReautenticacaoNecessaria();
            return throwError(() => error);
          case 'token':
            if (!podeRenovar || !authService.isAuthenticated() || !authService.podeRenovar()) {
              return throwError(() => error);
            }
            return from(authService.renovarToken(token)).pipe(
              catchError(() => throwError(() => error)),
              switchMap((novo) => tratar(novo, false)),
            );
        }
      }),
    );

  const atual = authService.token() ?? null;
  if (atual && authService.tokenVencido(atual) && authService.podeRenovar()) {
    return from(authService.tokenAtualizado()).pipe(
      switchMap((token) => tratar(token, token === atual)),
    );
  }
  return tratar(atual, true);
};

function enviar(next: HttpHandlerFn, req: HttpRequest<unknown>, token: string | null | undefined) {
  const headers: Record<string, string> = { 'X-Requested-With': 'XMLHttpRequest' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  return next(req.clone({ setHeaders: headers }));
}
