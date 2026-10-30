import { HttpErrorResponse, HttpEvent, HttpHandlerFn, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, catchError, from, switchMap, throwError } from 'rxjs';
import { TokenService } from '../auth/token.service';
import { AuthService, classificar401 } from '../auth/auth.service';

/**
 * Rotas que se autenticam pelo cookie da sessão, nunca pelo Bearer. Enviar um token
 * antigo nelas faria o filtro JWT do backend recusar antes de chegar ao endpoint.
 */
const ROTAS_DE_SESSAO = /\/v1\/auth\/(login|refresh|logout)$/;

/**
 * Anexa o token de acesso e resolve o 401 de token vencido sem sair da tela:
 *
 * - token já vencido e com rede: renova antes de enviar;
 * - 401 de token (`TOKEN_EXPIRADO`/`TOKEN_INVALIDO`): renova uma vez e repete;
 * - 401 `SESSAO_INVALIDA` ou renovação impossível (sem rede): devolve o 401 para o
 *   `errorInterceptor`, que marca a sessão e deixa a fila guardar o item.
 *
 * Fica DEPOIS do `errorInterceptor` na cadeia para tratar o `HttpErrorResponse` cru,
 * antes de ele virar `ApiError` e disparar toast.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const tokenService = inject(TokenService);
  const authService = inject(AuthService);

  if (ROTAS_DE_SESSAO.test(req.url)) {
    return next(req.clone({ withCredentials: true }));
  }

  const tratar = (token: string | null, podeRenovar: boolean): Observable<HttpEvent<unknown>> =>
    enviar(next, req, token).pipe(
      catchError((error: unknown) => {
        if (
          !(error instanceof HttpErrorResponse) || error.status !== 401 || !podeRenovar ||
          classificar401(error.error) === 'sessao-invalida' || !authService.podeRenovar()
        ) {
          return throwError(() => error);
        }
        return from(authService.renovarToken(token)).pipe(
          catchError(() => throwError(() => error)),
          switchMap(novo => tratar(novo, false))
        );
      })
    );

  const atual = tokenService.token();
  if (atual && tokenService.isTokenExpired(atual) && authService.podeRenovar()) {
    return from(authService.tokenAtualizado()).pipe(switchMap(token => tratar(token, token === atual)));
  }
  return tratar(atual, true);
};

function enviar(next: HttpHandlerFn, req: HttpRequest<unknown>, token: string | null) {
  return next(token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req);
}
