import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { db } from '../db/app-db';
import { OutboxService } from './outbox.service';
import { SyncService } from './sync.service';
import { ConnectivityService } from './connectivity.service';
import { ItemFilaDescrito, descreverItemFila } from '../campo/estado-envio';

/**
 * Visão da fila de envio para a tela: o que está esperando, enviando, com erro
 * ou em conflito. Recarrega sozinha quando a fila muda (envio terminou, item novo).
 */
@Injectable({ providedIn: 'root' })
export class FilaEnvioService {
  private readonly outbox = inject(OutboxService);
  private readonly sync = inject(SyncService);
  private readonly rede = inject(ConnectivityService);

  readonly itens = signal<ItemFilaDescrito[]>([]);
  readonly enviandoAgora = signal(false);
  readonly total = computed(() => this.outbox.pendingCount());
  readonly comProblema = computed(() => this.itens().filter(i => i.estado === 'error' || i.estado === 'conflict').length);

  constructor() {
    effect(() => {
      // Dependências: contagem da fila e cada rodada de envio.
      this.outbox.pendingCount();
      this.sync.outboxSyncVersion();
      untracked(() => void this.recarregar());
    });
  }

  async recarregar(): Promise<void> {
    const brutos = await db.outbox.where('status').anyOf(['PENDENTE', 'ERRO', 'RESULTADO_INCERTO']).sortBy('criadoEm');
    const enviando = this.enviandoAgora();
    this.itens.set(brutos.map(i => descreverItemFila(i, enviando && i.status === 'PENDENTE')));
  }

  /** "Enviar agora". Sem rede, não tenta: a fila sai sozinha quando o sinal voltar. */
  async enviarAgora(): Promise<'enviado' | 'sem-rede' | 'falhou'> {
    if (!this.rede.isOnline()) return 'sem-rede';
    this.enviandoAgora.set(true);
    await this.recarregar();
    try {
      await this.sync.syncOutbox();
      return 'enviado';
    } catch {
      return 'falhou';
    } finally {
      this.enviandoAgora.set(false);
      await this.recarregar();
    }
  }
}
