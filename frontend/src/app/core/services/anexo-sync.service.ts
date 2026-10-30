import { HttpClient } from '@angular/common/http';
import { Injectable, effect, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AnexoDesvioResponse } from '../models/sst.models';
import { ConnectivityService } from './connectivity.service';
import { AuthService } from './auth.service';
import { respostaTransitoria } from './sync.service';
import { DatabaseService, PendingAnexo } from './database.service';
import { HEADER_IDEMPOTENCIA, novaChaveIdempotencia } from '../utils/idempotencia.util';

/**
 * Fila offline-first para upload de anexos (evidências) de desvios.
 *
 * Os binários não passam pelo `SyncService` (que serializa JSON): são
 * persistidos como `Blob` na tabela Dexie `anexoQueue` junto dos IDs já
 * resolvidos de inspeção/desvio, e reenviados automaticamente quando a
 * conectividade é restabelecida.
 */
@Injectable({
  providedIn: 'root'
})
export class AnexoSyncService {
  private http = inject(HttpClient);
  private db = inject(DatabaseService);
  private connectivity = inject(ConnectivityService);
  private auth = inject(AuthService);

  /** Anexos aguardando envio — para feedback de UI (badge "aguardando"). */
  readonly pendentes = signal<PendingAnexo[]>([]);

  /**
   * Anexos sincronizados com sucesso pela fila (resposta real do servidor).
   * A UI observa este sinal para trocar o item temporário pelo definitivo,
   * sem precisar recarregar a inspeção inteira.
   */
  readonly sincronizados = signal<{ idDesvio: string; anexo: AnexoDesvioResponse }[]>([]);

  private flushing = false;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    this.refreshPendentes();
    // Reenvia a fila assim que o dispositivo volta a ficar online.
    effect(() => {
      const online = this.connectivity.isOnline();
      const usuario = this.auth.currentUser()?.id;
      const aguardandoLogin = this.auth.reautenticacaoNecessaria();
      if (online && usuario && !aguardandoLogin) {
        this.flush();
      }
    });

    // No mobile, voltar da câmera nativa NÃO dispara o evento 'online'
    // (o navigator.onLine permanece true), então o effect acima nunca
    // re-executa. Re-tentamos a fila quando a aba volta ao foco/visível
    // — que é exatamente o momento em que o WebService retorna da câmera.
    window.addEventListener('online', () => this.flush());
    window.addEventListener('focus', () => this.flush());
    window.addEventListener('pageshow', () => this.flush());
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') this.flush();
    });
  }

  private anexosUrl(idInspecao: string, idDesvio: string): string {
    return `${environment.apiUrl}/v1/sst/inspecoes/${idInspecao}/desvios/${idDesvio}/anexos`;
  }

  /** Quantos anexos de um desvio específico ainda aguardam envio. */
  pendentesDoDesvio(idDesvio: string): number {
    return this.pendentes().filter((a) => a.idDesvio === idDesvio).length;
  }

  /**
   * Enfileira um anexo para envio (e tenta enviar de imediato, se online).
   * Retorna o id gerado na fila, para que a UI possa exibir/reconciliar o
   * item pendente mesmo após um reload.
   */
  async enqueue(item: Omit<PendingAnexo, 'id' | 'timestamp' | 'chaveIdempotencia'>): Promise<number> {
    // A chave nasce no enfileiramento, não no envio: todas as tentativas deste anexo — a
    // imediata e as do backoff — precisam compartilhá-la. Sem isso, uma foto de desvio
    // cuja resposta se perde na volta é anexada de novo a cada retentativa.
    const id = (await this.db.anexoQueue.add({
      ...item,
      usuarioId: this.auth.currentUser()?.id,
      timestamp: Date.now(),
      chaveIdempotencia: novaChaveIdempotencia()
    })) as number;
    await this.refreshPendentes();
    if (this.connectivity.isOnline()) {
      // Tenta de imediato; se falhar (rede ainda instável ao voltar da câmera),
      // o scheduleRetry abaixo cuida das próximas tentativas com backoff.
      this.flush();
      this.scheduleRetry(2000);
    }
    return id;
  }

  /**
   * Agenda uma re-tentativa de flush enquanto restarem pendências. Backoff
   * exponencial (2s → 4s → ... → 30s) para cobrir a janela em que a rede
   * ainda está se restabelecendo após o retorno da câmera nativa no mobile.
   */
  private scheduleRetry(delay = 2000): void {
    if (this.retryTimer) return;
    this.retryTimer = setTimeout(async () => {
      this.retryTimer = null;
      await this.flush();
      if (this.pendentes().length && this.connectivity.isOnline()) {
        this.scheduleRetry(Math.min(delay * 2, 30000));
      }
    }, delay);
  }

  /** Processa a fila pendente em ordem de chegada. */
  async flush(): Promise<void> {
    if (this.flushing || !this.connectivity.isOnline()) return;
    const usuarioId = this.auth.currentUser()?.id;
    if (!usuarioId || this.auth.reautenticacaoNecessaria()) return;
    this.flushing = true;
    try {
      const fila = (await this.db.anexoQueue.orderBy('timestamp').toArray()).filter(
        (item) => !item.usuarioId || item.usuarioId === usuarioId
      );
      for (const item of fila) {
        try {
          const fd = new FormData();
          fd.append('contexto', item.contexto);
          fd.append('arquivo', new File([item.blob], item.nomeArquivo, { type: item.contentType }));

          // Item enfileirado antes de a fila carimbar chaves: uma chave gerada agora já
          // protege as próximas tentativas deste mesmo anexo.
          const chave = item.chaveIdempotencia ?? novaChaveIdempotencia();
          if (!item.chaveIdempotencia) {
            await this.db.anexoQueue.update(item.id!, { chaveIdempotencia: chave });
          }

          const anexo = await firstValueFrom(
            this.http.post<AnexoDesvioResponse>(this.anexosUrl(item.idInspecao, item.idDesvio), fd, {
              headers: { [HEADER_IDEMPOTENCIA]: chave }
            })
          );
          await this.db.anexoQueue.delete(item.id!);
          this.sincronizados.update((l) => [...l, { idDesvio: item.idDesvio, anexo }]);
        } catch (err: any) {
          const status = err?.status ?? 0;
          // Erro de validação/permissão (4xx definitivo): descarta para não travar a fila.
          // Rede (0), sessão a renovar (401), limite de taxa ou servidor (5xx): mantém e
          // tenta no próximo evento online.
          if (!respostaTransitoria(status) && status >= 400 && status < 500) {
            await this.db.anexoQueue.delete(item.id!);
          } else {
            break;
          }
        }
      }
    } finally {
      this.flushing = false;
      await this.refreshPendentes();
      // Sobrou pendência (rede/servidor)? Garante uma nova tentativa mesmo
      // que nenhum evento de conectividade/foco volte a ocorrer.
      if (this.pendentes().length && this.connectivity.isOnline() && !this.auth.reautenticacaoNecessaria()) {
        this.scheduleRetry(2000);
      }
    }
  }

  /** Anexos de um desvio específico ainda aguardando envio (com blob). */
  pendingDoDesvio(idDesvio: string): PendingAnexo[] {
    return this.pendentes().filter((a) => a.idDesvio === idDesvio);
  }

  /** Remove um anexo ainda pendente da fila (usuário cancelou antes do envio). */
  async remover(id: number): Promise<void> {
    await this.db.anexoQueue.delete(id);
    await this.refreshPendentes();
  }

  private async refreshPendentes(): Promise<void> {
    this.pendentes.set(await this.db.anexoQueue.toArray());
  }
}
