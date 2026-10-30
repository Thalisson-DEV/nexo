import { Injectable } from '@angular/core';
import Dexie, { Table } from 'dexie';
import { ColaboradorResponse } from '../models/rh.models';
import { ContextoAnexo } from '../models/sst.models';

export interface PendingSync {
  id?: number;
  url: string;
  method: 'POST' | 'PUT' | 'DELETE' | 'PATCH';
  body: any;
  timestamp: number;
  /**
   * Chave de idempotência da operação, gravada JUNTO do item da fila.
   *
   * É o que impede a fila de duplicar. Um erro de rede é ausência de resposta, não prova
   * de que nada aconteceu no servidor: a requisição pode ter chegado, gravado e se perdido
   * só na volta. Reenviando com a MESMA chave, quem decide entre "não chegou, execute" e
   * "já executei, tome a resposta de novo" é o backend — que é o único lado que sabe.
   */
  chaveIdempotencia?: string;
  /**
   * Quantas vezes a fila já tentou reenviar. Existe para que um item impossível de
   * concluir pare de ser tentado a cada evento de conectividade — sem o teto, ele
   * atravessa a fila inteira em toda reconexão e atrasa o que ainda tem chance.
   */
  tentativas?: number;
  /**
   * Quem lançou. A fila só envia os itens do usuário logado: num aparelho compartilhado,
   * o lançamento de quem saiu não pode chegar ao servidor com o token de quem entrou.
   * Itens antigos, sem dono, seguem com o usuário atual.
   */
  usuarioId?: string;
}

/**
 * Anexo (evidência) de desvio aguardando envio. Diferente da `syncQueue`
 * (JSON), guarda o binário e os IDs já resolvidos da inspeção/desvio.
 */
export interface PendingAnexo {
  id?: number;
  idInspecao: string;
  idDesvio: string;
  contexto: ContextoAnexo;
  nomeArquivo: string;
  contentType: string;
  blob: Blob;
  timestamp: number;
  /** Ver `PendingSync.chaveIdempotencia` — mesma razão, aplicada ao binário. */
  chaveIdempotencia?: string;
  /** Ver `PendingSync.usuarioId`. */
  usuarioId?: string;
}

@Injectable({
  providedIn: 'root'
})
export class DatabaseService extends Dexie {
  colaboradores!: Table<ColaboradorResponse, string>;
  syncQueue!: Table<PendingSync, number>;
  anexoQueue!: Table<PendingAnexo, number>;

  constructor() {
    super('CesDatabase');

    this.version(1).stores({
      colaboradores: 'id, nome, cpf, ativo', // id é a PK
      syncQueue: '++id, timestamp' // id autoincrement para fila de sincronização
    });

    // v2: fila de upload de anexos de desvios (offline-first)
    this.version(2).stores({
      colaboradores: 'id, nome, cpf, ativo',
      syncQueue: '++id, timestamp',
      anexoQueue: '++id, idDesvio, timestamp'
    });

    // `chaveIdempotencia` e `tentativas` NÃO aparecem aqui de propósito: o `stores()` do
    // Dexie declara índices, não colunas. Campo não indexado é gravado sem migration —
    // e indexá-los não serviria a nenhuma consulta que a fila faça.
  }

  async clearDatabase() {
    await this.colaboradores.clear();
    await this.syncQueue.clear();
    await this.anexoQueue.clear();
  }
}
