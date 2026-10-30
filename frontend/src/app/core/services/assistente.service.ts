import { HttpClient, HttpEvent, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AcaoCartao,
  AlteracaoCartao,
  ArquivoAssistente,
  ArtefatoAssistente,
  CapacidadesAssistente,
  ConversaDetalhe,
  ConversaResumo,
  CriarConversaRequest,
  EnviarMensagemRequest,
  OpcaoCadastro,
  PacoteAssistente,
  ResultadoTabular,
} from '../models/assistente.models';
import { PagedResponse, ProblemDetails } from '../models/shared.models';
import { AuthService } from './auth.service';

/** Falha HTTP do stream, com a mensagem de negócio do Problem Details quando houver. */
export class AssistenteStreamError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
    this.name = 'AssistenteStreamError';
  }
}

/**
 * Separa um buffer NDJSON em linhas completas; o resto (linha incompleta) volta para o próximo pedaço.
 * Linhas vazias e inválidas são descartadas sem interromper o stream.
 */
export function dividirNdjson(buffer: string): { pacotes: PacoteAssistente[]; resto: string } {
  const pacotes: PacoteAssistente[] = [];
  let inicio = 0;
  let fim = buffer.indexOf('\n', inicio);
  while (fim >= 0) {
    const linha = buffer.slice(inicio, fim).trim();
    if (linha) {
      try {
        const pacote = JSON.parse(linha) as PacoteAssistente;
        if (pacote && typeof pacote === 'object' && pacote.obj && typeof pacote.obj.type === 'string') {
          pacotes.push(pacote);
        }
      } catch {
        // linha corrompida: ignorada (o cursor de retomada conta apenas pacotes válidos)
      }
    }
    inicio = fim + 1;
    fim = buffer.indexOf('\n', inicio);
  }
  return { pacotes, resto: buffer.slice(inicio) };
}

/**
 * Cliente da API do Copiloto. Operações REST passam pelo HttpClient (interceptors de autenticação e
 * idempotência); o streaming NDJSON usa `fetch` + `ReadableStream`, pois o HttpClient não entrega o corpo
 * em partes. O stream não leva chave de idempotência de propósito: o filtro do backend bufferizaria a
 * resposta inteira.
 */
@Injectable({ providedIn: 'root' })
export class AssistenteService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly base = `${environment.apiUrl}/v1/assistente`;

  /** Conversas do usuário; `painel` restringe às iniciadas naquele dashboard (Copiloto contextual). */
  listarConversas(pagina = 0, tamanho = 40, busca?: string, painel?: string): Observable<PagedResponse<ConversaResumo>> {
    let params = new HttpParams().set('pagina', pagina).set('tamanho', tamanho);
    if (busca?.trim()) params = params.set('busca', busca.trim());
    if (painel?.trim()) params = params.set('painel', painel.trim());
    return this.http.get<PagedResponse<ConversaResumo>>(`${this.base}/conversas`, { params });
  }

  criarConversa(request: CriarConversaRequest = {}): Observable<ConversaResumo> {
    return this.http.post<ConversaResumo>(`${this.base}/conversas`, request);
  }

  buscarConversa(conversaId: string): Observable<ConversaDetalhe> {
    return this.http.get<ConversaDetalhe>(`${this.base}/conversas/${conversaId}`);
  }

  renomearConversa(conversaId: string, titulo: string): Observable<ConversaResumo> {
    return this.http.patch<ConversaResumo>(`${this.base}/conversas/${conversaId}`, { titulo });
  }

  excluirConversa(conversaId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/conversas/${conversaId}`);
  }

  parar(conversaId: string): Observable<{ interrompida: boolean }> {
    return this.http.post<{ interrompida: boolean }>(`${this.base}/conversas/${conversaId}/parar`, {});
  }

  listarArtefatos(conversaId: string): Observable<ArtefatoAssistente[]> {
    return this.http.get<ArtefatoAssistente[]>(`${this.base}/conversas/${conversaId}/artefatos`);
  }

  resultado(conversaId: string, codigo: string, limite = 200): Observable<ResultadoTabular> {
    return this.http.get<ResultadoTabular>(`${this.base}/conversas/${conversaId}/resultados/${codigo}`, {
      params: new HttpParams().set('limite', limite),
    });
  }

  capacidades(): Observable<CapacidadesAssistente> {
    return this.http.get<CapacidadesAssistente>(`${this.base}/capacidades`);
  }

  enviarArquivo(arquivo: File): Observable<HttpEvent<ArquivoAssistente>> {
    const corpo = new FormData();
    corpo.append('arquivo', arquivo, arquivo.name);
    return this.http.post<ArquivoAssistente>(`${this.base}/arquivos`, corpo, {
      reportProgress: true,
      observe: 'events',
    });
  }

  removerArquivo(arquivoId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/arquivos/${arquivoId}`);
  }

  baixarArquivo(arquivoId: string, download = true): Observable<Blob> {
    return this.http.get(`${this.base}/arquivos/${arquivoId}/conteudo`, {
      params: new HttpParams().set('download', download),
      responseType: 'blob',
    });
  }

  /** Estado atual dos cartões de confirmação da conversa (confirmados, cancelados, pendentes). */
  listarAcoes(conversaId: string): Observable<AcaoCartao[]> {
    return this.http.get<AcaoCartao[]>(`${this.base}/conversas/${conversaId}/acoes`);
  }

  /** Aplica ajustes ao cartão sem gravar; devolve a prévia recalculada. */
  revisarAcao(acaoId: string, alteracoes: AlteracaoCartao[]): Observable<AcaoCartao> {
    return this.http.post<AcaoCartao>(`${this.base}/acoes/${acaoId}/revisao`, { alteracoes });
  }

  /** Confirma o cartão: grava pelos casos de uso do módulo. */
  confirmarAcao(acaoId: string, alteracoes: AlteracaoCartao[] = []): Observable<AcaoCartao> {
    return this.http.post<AcaoCartao>(`${this.base}/acoes/${acaoId}/confirmacao`, { alteracoes });
  }

  cancelarAcao(acaoId: string): Observable<AcaoCartao> {
    return this.http.post<AcaoCartao>(`${this.base}/acoes/${acaoId}/cancelamento`, {});
  }

  /** Busca registros de um cadastro referenciado no cartão (equipe, material, fornecedor...). */
  buscarReferencia(tipo: string, termo: string): Observable<OpcaoCadastro[]> {
    return this.http.get<OpcaoCadastro[]>(`${this.base}/cadastros/referencias/${tipo}`, {
      params: new HttpParams().set('termo', termo),
    });
  }

  /** Envia a pergunta e entrega cada pacote do stream até o fim da execução. */
  transmitir(
    conversaId: string,
    request: EnviarMensagemRequest,
    aoPacote: (pacote: PacoteAssistente) => void,
    sinal: AbortSignal,
  ): Promise<void> {
    return this.lerStream(`${this.base}/conversas/${conversaId}/mensagens/stream`, {
      method: 'POST',
      body: JSON.stringify(request),
      signal: sinal,
    }, aoPacote);
  }

  /** Retoma o stream da execução da conversa a partir de `cursor` pacotes já recebidos. */
  retomar(
    conversaId: string,
    cursor: number,
    aoPacote: (pacote: PacoteAssistente) => void,
    sinal: AbortSignal,
  ): Promise<void> {
    return this.lerStream(`${this.base}/conversas/${conversaId}/stream?cursor=${Math.max(0, cursor)}`, {
      method: 'GET',
      signal: sinal,
    }, aoPacote);
  }

  private async lerStream(
    url: string,
    init: RequestInit,
    aoPacote: (pacote: PacoteAssistente) => void,
  ): Promise<void> {
    const headers: Record<string, string> = {
      Accept: 'application/x-ndjson, application/problem+json, application/json',
      'X-Requested-With': 'XMLHttpRequest',
    };
    if (init.body) headers['Content-Type'] = 'application/json';
    // fetch não passa pelo interceptor: renova aqui um token já vencido.
    const token = await this.auth.tokenAtualizado();
    if (token) headers['Authorization'] = `Bearer ${token}`;

    const resposta = await fetch(url, { ...init, headers, cache: 'no-store' });
    if (!resposta.ok) throw await this.erroHttp(resposta);
    if (!resposta.body) throw new AssistenteStreamError('O navegador não suporta respostas em streaming.', 0);

    const leitor = resposta.body.getReader();
    const decodificador = new TextDecoder();
    let buffer = '';
    for (;;) {
      const { value, done } = await leitor.read();
      if (done) break;
      buffer += decodificador.decode(value, { stream: true });
      const { pacotes, resto } = dividirNdjson(buffer);
      buffer = resto;
      pacotes.forEach(aoPacote);
    }
    buffer += decodificador.decode();
    dividirNdjson(buffer + '\n').pacotes.forEach(aoPacote);
  }

  private async erroHttp(resposta: Response): Promise<AssistenteStreamError> {
    let problema: ProblemDetails | undefined;
    try {
      problema = (await resposta.json()) as ProblemDetails;
    } catch {
      problema = undefined;
    }
    if (resposta.status === 401) {
      // Nunca desloga: renova o token ou marca a sessão para novo login, como o interceptor.
      this.auth.tratarNaoAutorizado(problema);
      return new AssistenteStreamError(
        this.auth.reautenticacaoNecessaria()
          ? 'Sua sessão terminou. Entre novamente para continuar.'
          : 'Sua sessão foi renovada. Envie a mensagem de novo.',
        401,
      );
    }
    if (resposta.status === 403) {
      return new AssistenteStreamError('Seu perfil não tem acesso ao Copiloto.', 403);
    }
    const detalhe = problema?.detail || problema?.title;
    return new AssistenteStreamError(
      detalhe || `Não foi possível falar com o Copiloto (HTTP ${resposta.status}).`,
      resposta.status,
    );
  }
}
