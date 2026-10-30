import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, effect } from '@angular/core';
import { DatabaseService, PendingSync } from './database.service';
import { ConnectivityService } from './connectivity.service';
import { AuthService } from './auth.service';
import { firstValueFrom } from 'rxjs';
import { HEADER_IDEMPOTENCIA, novaChaveIdempotencia } from '../utils/idempotencia.util';

/**
 * Depois de quantas tentativas a fila desiste de um item.
 *
 * Um item que falhou 8 vezes contra um servidor que respondeu não vai passar na nona;
 * insistir só atrasa o que ainda tem chance. Como cada tentativa acontece em um evento
 * de conectividade distinto, 8 cobre dias de oscilação de sinal em campo.
 */
const MAXIMO_DE_TENTATIVAS = 8;

/**
 * Respostas que não são veredito sobre o pedido: sessão a renovar (401), timeout (408),
 * servidor ainda não pronto (425) e limite de taxa (429). O item fica na fila, intacto.
 */
export function respostaTransitoria(status: number): boolean {
  return status === 0 || status === 401 || status === 408 || status === 425 || status === 429;
}

@Injectable({
  providedIn: 'root'
})
export class SyncService {
  private http = inject(HttpClient);
  private db = inject(DatabaseService);
  private connectivity = inject(ConnectivityService);
  private auth = inject(AuthService);

  private processando = false;

  constructor() {
    // Sincroniza quando o aparelho volta a ficar online e quando o usuário volta a entrar
    // (reautenticação ou troca de usuário): só então a fila tem com que credencial enviar.
    effect(() => {
      const online = this.connectivity.isOnline();
      const usuario = this.auth.currentUser()?.id;
      const aguardandoLogin = this.auth.reautenticacaoNecessaria();
      if (online && usuario && !aguardandoLogin) {
        this.processQueue();
      }
    });
  }

  /**
   * Executa uma requisição. Se estiver offline, enfileira para sincronização posterior.
   *
   * <p>A chave de idempotência nasce aqui, uma por operação, e acompanha o item se ele
   * for parar na fila. É o que torna o reenvio seguro: sem ela, um POST que chegou ao
   * servidor mas cuja resposta se perdeu no caminho de volta seria reenviado e gravaria
   * o mesmo registro duas vezes — que é exatamente como um desconto virou três.</p>
   */
  async execute<T>(
    method: 'POST' | 'PUT' | 'DELETE' | 'PATCH',
    url: string,
    body: any,
    chaveDoChamador?: string
  ): Promise<T | null> {
    // Chamador que sabe retentar (o formulário de desconto, que retoma de onde parou)
    // traz a sua própria chave, estável entre as tentativas. Quem não traz ganha uma
    // por execução — suficiente para cobrir o reenvio da fila, que é a retentativa que
    // este serviço mesmo faz.
    const chaveIdempotencia = chaveDoChamador ?? novaChaveIdempotencia();

    if (!this.connectivity.isOnline()) {
      await this.enfileirar({ method, url, body, chaveIdempotencia });
      console.warn('Operação enfileirada para sincronização offline.');
      return null;
    }

    try {
      return await firstValueFrom(this.enviar<T>(method, url, body, chaveIdempotencia));
    } catch (error) {
      // Status 0 (rede caiu / requisição abortada) e 5xx (servidor reiniciando, deploy no
      // meio do caminho) são desfechos DESCONHECIDOS: podem ter gravado. Enfileirar com a
      // mesma chave deixa o backend resolver a ambiguidade no reenvio. 4xx é resposta
      // definitiva sobre o pedido — sobe para quem chamou. 401 também enfileira: a sessão
      // precisa ser renovada ou refeita, e o lançamento não pode se perder por isso.
      if (error instanceof HttpErrorResponse && (respostaTransitoria(error.status) || error.status >= 500)) {
        await this.enfileirar({ method, url, body, chaveIdempotencia });
        return null;
      }
      throw error;
    }
  }

  private enviar<T>(method: string, url: string, body: any, chaveIdempotencia: string) {
    return this.http.request<T>(method, url, {
      body,
      headers: { [HEADER_IDEMPOTENCIA]: chaveIdempotencia }
    });
  }

  private async enfileirar(item: Omit<PendingSync, 'id' | 'timestamp' | 'tentativas'>): Promise<void> {
    await this.db.syncQueue.add({
      ...item,
      usuarioId: this.auth.currentUser()?.id,
      timestamp: Date.now(),
      tentativas: 0
    });
  }

  /**
   * Processa a fila de sincronização pendente.
   *
   * <p>O guarda de reentrância importa: `processQueue` é disparada por um `effect` de
   * conectividade que pode reexecutar durante o processamento. Sem ele, duas passagens
   * concorrentes leriam a mesma fila e enviariam os mesmos itens em paralelo.</p>
   */
  private async processQueue() {
    if (this.processando) return;
    const usuarioId = this.auth.currentUser()?.id;
    if (!usuarioId || this.auth.reautenticacaoNecessaria()) return;
    this.processando = true;

    try {
      const queue = (await this.db.syncQueue.orderBy('timestamp').toArray()).filter(
        (item) => !item.usuarioId || item.usuarioId === usuarioId
      );
      if (queue.length === 0) return;

      console.info(`Sincronizando ${queue.length} operações pendentes...`);

      for (const item of queue) {
        if (!this.connectivity.isOnline()) break;

        let body = item.body;
        if (body && typeof body === 'object') {
          if ('numPoste' in body) delete body.numPoste;
          if ('numPosto' in body) delete body.numPosto;
        }

        // Item antigo, gravado antes de a fila carimbar chaves: uma chave gerada agora
        // ainda protege contra a repetição das PRÓXIMAS tentativas deste mesmo item.
        const chave = item.chaveIdempotencia ?? novaChaveIdempotencia();
        if (!item.chaveIdempotencia) {
          await this.db.syncQueue.update(item.id!, { chaveIdempotencia: chave });
        }

        try {
          await firstValueFrom(this.enviar(item.method, item.url, body, chave));
          await this.db.syncQueue.delete(item.id!);
        } catch (err) {
          const status = err instanceof HttpErrorResponse ? err.status : 0;

          if (respostaTransitoria(status)) {
            // Sem rede, sessão a renovar ou servidor pedindo calma: o servidor não recusou o
            // pedido. Mantém o item sem queimar tentativas e preserva a ORDEM da fila.
            console.warn(`Sincronização pausada (HTTP ${status}). Item mantido na fila:`, item);
            break;
          }

          if (status >= 400 && status < 500) {
            // Recusa definitiva do servidor (validação, permissão, recurso que sumiu).
            // Retentar não muda o veredito e travaria a fila para sempre.
            console.error('Item descartado da fila de sincronização (recusado pelo servidor):', item, err);
            await this.db.syncQueue.delete(item.id!);
            continue;
          }

          const tentativas = (item.tentativas ?? 0) + 1;
          if (tentativas >= MAXIMO_DE_TENTATIVAS) {
            console.error(`Item descartado após ${tentativas} tentativas de sincronização:`, item, err);
            await this.db.syncQueue.delete(item.id!);
            continue;
          }

          await this.db.syncQueue.update(item.id!, { tentativas });
          // Servidor temporariamente indisponível (5xx): interrompe o lote para manter a ORDEM
          break;
        }
      }
    } finally {
      this.processando = false;
    }
  }
}
