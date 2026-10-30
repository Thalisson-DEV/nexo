import { Injectable, inject } from '@angular/core';
import { SESSAO_DB, EventoLocal } from './sessao-db';

/** Tipos de evento aceitos. Nada aqui carrega nome, nota, coordenada ou texto digitado. */
export type TipoEvento =
  | 'app.abertura'
  | 'retomada.decisao'
  | 'retomada.falha'
  | 'rascunho.restaurado'
  | 'rascunho.conflito'
  | 'armazenamento.cheio'
  | 'armazenamento.persistencia'
  | 'sync.falha'
  | 'sw.atualizacao'
  | 'instalacao'
  | 'erro.cliente';

export const LIMITE_EVENTOS = 500;

/**
 * Registro local de diagnóstico (LGPD: só tipos, contagens, durações e motivos
 * técnicos). O envio ao servidor acontece em lote pelo `TelemetriaEnvioService`.
 * Nunca lança: telemetria não pode derrubar o app.
 */
@Injectable({ providedIn: 'root' })
export class TelemetriaService {
  private readonly db = inject(SESSAO_DB);

  async registrar(tipo: TipoEvento, dados?: EventoLocal['dados']): Promise<void> {
    try {
      await this.db.transaction('rw', this.db.eventos, async () => {
        await this.db.eventos.add({ tipo, em: Date.now(), dados: limpar(dados), enviado: 0 });
        const total = await this.db.eventos.count();
        if (total > LIMITE_EVENTOS) {
          const excesso = await this.db.eventos.orderBy('seq').limit(total - LIMITE_EVENTOS).primaryKeys();
          await this.db.eventos.bulkDelete(excesso);
        }
      });
    } catch {
      /* sem espaço ou banco indisponível: descarta o evento */
    }
  }

  async pendentes(limite = 100): Promise<EventoLocal[]> {
    return this.db.eventos.where('enviado').equals(0).limit(limite).toArray();
  }

  async marcarEnviados(seqs: number[]): Promise<void> {
    await this.db.eventos.where('seq').anyOf(seqs).modify({ enviado: 1 });
  }

  async recentes(limite = 50): Promise<EventoLocal[]> {
    return this.db.eventos.orderBy('seq').reverse().limit(limite).toArray();
  }
}

/** Corta strings longas (evita vazar texto livre por engano) e descarta não primitivos. */
function limpar(dados?: EventoLocal['dados']): EventoLocal['dados'] {
  if (!dados) return undefined;
  const out: Record<string, string | number | boolean> = {};
  for (const [k, v] of Object.entries(dados)) {
    if (typeof v === 'string') out[k] = v.slice(0, 80);
    else if (typeof v === 'number' || typeof v === 'boolean') out[k] = v;
  }
  return out;
}
