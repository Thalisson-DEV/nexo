import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService, classificar401 } from '../auth/auth.service';
import { ToastService } from '../ui/toast.service';
import { SILENT_ERROR } from './http-context';

export interface ApiError {
  type?: string;
  title: string;
  status: number;
  detail: string;
  instance?: string;
  timestamp?: string;
  erros?: Record<string, string | string[]>;
  /** Compatibilidade com APIs antigas que usavam `errors`. */
  errors?: Record<string, string[]>;
}

/**
 * Extrai o corpo Problem Details (RFC 7807) quando ele existe de fato.
 * Em falhas de rede o Angular preenche `error.error` com um `ProgressEvent`,
 * que é um objeto mas não carrega nenhum campo útil — precisa ser descartado.
 */
function extrairProblemDetails(error: HttpErrorResponse): Partial<ApiError> | null {
  const body = error.error;

  if (!body || typeof body !== 'object' || body instanceof ProgressEvent) {
    return null;
  }

  const problem = body as Partial<ApiError>;
  const temConteudoUtil = !!problem.detail || !!problem.title;

  return temConteudoUtil ? problem : null;
}

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const toastService = inject(ToastService);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      const problem = extrairProblemDetails(error);

      let apiError: ApiError = {
        title: 'Erro inesperado',
        status: error.status,
        detail: error.message || 'Ocorreu um erro ao processar a requisição.'
      };

      if (error.status === 504) {
        apiError = {
          type: 'https://tools.ietf.org/html/rfc7231#section-6.6.5',
          title: 'Tempo limite do gateway excedido (504)',
          status: 504,
          detail: 'O servidor demorou muito para responder (Erro 504 - Gateway Timeout). A conexão em campo pode estar instável ou o sistema temporariamente sobrecarregado.'
        };
      } else if (error.status === 502 || error.status === 503) {
        apiError = {
          type: 'https://tools.ietf.org/html/rfc7231#section-6.6',
          title: `Servidor temporariamente indisponível (${error.status})`,
          status: error.status,
          detail: 'O serviço está temporariamente indisponível ou em manutenção. O aplicativo continuará operando com os dados em cache.'
        };
      } else if (error.status === 408 || error.status === 0) {
        apiError = {
          title: 'Sem resposta do servidor',
          status: error.status || 0,
          detail: 'A conexão com o servidor falhou. Verifique sua conectividade.'
        };
      } else if (error.status === 401 && !/\/v1\/auth\//.test(req.url)) {
        // Chega aqui só o 401 que o authInterceptor não resolveu renovando: a sessão acabou
        // no servidor, ou não havia rede para renovar. Nos dois casos o lançamento fica no
        // aparelho e a pessoa continua na tela.
        const sessaoEncerrada = classificar401(error.error) === 'sessao-invalida';
        apiError = {
          ...apiError,
          ...(problem ?? {}),
          title: sessaoEncerrada ? 'Entre novamente' : 'Acesso a renovar',
          status: 401,
          detail: sessaoEncerrada
            ? 'Sua sessão terminou. Seus lançamentos continuam salvos no aparelho; entre de novo quando tiver internet para enviá-los.'
            : 'Não foi possível renovar o acesso agora. O que você lançou fica salvo e será enviado quando houver conexão.'
        };
      } else if (error.status === 403) {
        // Usuário autenticado, porém sem a permissão exigida pelo endpoint.
        // NÃO é motivo para deslogar — o token continua válido.
        apiError = {
          ...apiError,
          ...(problem ?? {}),
          title: problem?.title || 'Acesso não permitido',
          status: 403,
          detail: problem?.detail || 'Seu perfil de acesso não permite esta operação. Fale com o administrador do sistema.'
        };
      } else if (problem) {
        // Mapeamento RFC 7807 (Problem Details)
        apiError = {
          ...apiError,
          ...problem
        };
      }

      // Verifica se é uma verificação silenciosa ou consulta de turno ativo que retorna 404 (sem turno aberto)
      const isTurnoAtivoCheck = req.url.includes('/turnos/equipe/') && req.url.includes('/ativo') && (error.status === 404 || error.status === 400);
      const isSilentCheck = req.context.get(SILENT_ERROR) ||
        req.headers.has('X-Silent-Error') ||
        req.headers.has('X-Silent-Sync');

      if (!isTurnoAtivoCheck && !isSilentCheck) {
        // Mostrar toast de erro globalmente apenas quando não for verificação silenciosa
        toastService.error(apiError.detail || apiError.title);
      }

      if (error.status === 401 && !/\/v1\/auth\/(login|logout)$/.test(req.url) &&
          classificar401(error.error) === 'sessao-invalida') {
        authService.requireReauthentication();
      }

      return throwError(() => apiError);
    })
  );
};
