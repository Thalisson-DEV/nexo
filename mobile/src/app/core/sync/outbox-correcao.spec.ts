import { TestBed } from '@angular/core/testing';
import { CorrecaoRecusadaError, OutboxService } from './outbox.service';
import { db } from '../db/app-db';
import { ExecucaoCampoLocal, OutboxItem, SolicitacaoLocal } from '../db/models';

const LANCAR = '/_execucoes-comerciais/lancar';

function execucao(id: string, acoes: string[], extra: Partial<ExecucaoCampoLocal> = {}): ExecucaoCampoLocal {
  return {
    id, referenciaNota: '4605144730', idTurno: 't1', idEquipe: 'e1', dataLancamento: new Date().toISOString(),
    statusExecucao: 'CONCLUIDO', acoes, materiais: [], statusSync: 'LOCAL', ...extra,
  };
}

function intencao(tempId: string) {
  return { endpoint: LANCAR, method: 'POST' as const, payload: { tempId, body: {} } };
}

/**
 * Correção de nota na fila offline. Em campo (14/10) a versão corrigida e a anterior
 * foram enviadas juntas e a nota entrou duplicada em produção.
 */
describe('OutboxService — correção de registro na fila', () => {
  let outbox: OutboxService;

  beforeEach(async () => {
    await Promise.all([db.outbox.clear(), db.execucoesCampo.clear(), db.solicitacoes.clear()]);
    TestBed.configureTestingModule({});
    outbox = TestBed.inject(OutboxService);
  });

  afterAll(async () => {
    await Promise.all([db.outbox.clear(), db.execucoesCampo.clear(), db.solicitacoes.clear()]);
  });

  async function lancar(id: string, acoes: string[]): Promise<string> {
    return outbox.enqueueExecucao(intencao(id), execucao(id, acoes));
  }

  async function corrigir(antigo: string, novo: string, acoes: string[]): Promise<string> {
    const fila = await db.outbox.filter(i => i.payload?.tempId === antigo).first();
    return outbox.enqueueExecucao(intencao(novo), execucao(novo, acoes), antigo, fila?.id);
  }

  it('corrigir uma nota ainda não enviada deixa só a versão corrigida para enviar', async () => {
    await lancar('v1', ['a', 'b', 'c']);
    const novo = await corrigir('v1', 'v2', ['a', 'b']);

    const pendentes = await outbox.getPendingItems();
    expect(pendentes.map(i => i.id)).toEqual([novo]);
    expect(pendentes[0].payload.tempId).toBe('v2');
    expect(await db.execucoesCampo.get('v1')).toBeUndefined();
  });

  it('corrigir duas vezes seguidas continua deixando uma única versão', async () => {
    await lancar('v1', ['a']);
    await corrigir('v1', 'v2', ['b']);
    const ultima = await corrigir('v2', 'v3', ['c']);

    expect((await outbox.getPendingItems()).map(i => i.id)).toEqual([ultima]);
  });

  it('recusa a correção quando o envio da versão anterior já começou', async () => {
    const original = await lancar('v1', ['a', 'b', 'c']);
    expect(await outbox.reivindicarParaEnvio(original)).toBeDefined();

    await expectAsync(corrigir('v1', 'v2', ['a', 'b'])).toBeRejectedWithError(CorrecaoRecusadaError);

    expect((await db.outbox.toArray()).map(i => i.id)).toEqual([original]);
    expect(await db.execucoesCampo.get('v1')).toBeDefined();
    expect(await db.execucoesCampo.get('v2')).toBeUndefined();
  });

  it('nota com envio iniciado não pode ser corrigida nem descartada', async () => {
    const original = await lancar('v1', ['a']);
    const item = await outbox.reivindicarParaEnvio(original);

    expect(outbox.canEdit(item)).toBeFalse();
    expect(outbox.canDiscard(item)).toBeFalse();
  });

  it('recusa a correção de nota que já sincronizou enquanto o formulário estava aberto', async () => {
    const original = await lancar('v1', ['a']);
    // O sync troca o id local pelo do servidor e tira o item da fila.
    await db.execucoesCampo.delete('v1');
    await db.execucoesCampo.put(execucao('id-servidor', ['a'], { statusSync: 'SINCRONIZADO' }));
    await db.outbox.delete(original);

    await expectAsync(outbox.enqueueExecucao(intencao('v2'), execucao('v2', ['b']), 'v1', original))
      .toBeRejectedWithError(CorrecaoRecusadaError);
    await expectAsync(outbox.enqueueExecucao(intencao('v3'), execucao('v3', ['b']), 'id-servidor'))
      .toBeRejectedWithError(CorrecaoRecusadaError);
    expect(await db.outbox.count()).toBe(0);
  });

  it('o sync não reivindica uma versão que a correção já retirou da fila', async () => {
    const original = await lancar('v1', ['a']);
    const listados = await outbox.getPendingItems();
    await corrigir('v1', 'v2', ['b']);

    expect(listados.map(i => i.id)).toContain(original);
    expect(await outbox.reivindicarParaEnvio(original)).toBeUndefined();
  });

  it('pedido de material só é corrigido enquanto está na fila', async () => {
    const pedido = (id: string): SolicitacaoLocal => ({
      id, idEstoqueOrigem: 'o', idEstoqueDestino: 'd', idRequisitante: 'u', status: 'PENDENTE', itens: [],
    });
    const fila = (id: string) => ({ endpoint: '/v1/solicitacoes-material', method: 'POST' as const, payload: { tempId: id } });
    // Já sincronizado: está no aparelho com o id do servidor, fora da fila.
    await db.solicitacoes.put(pedido('id-servidor'));

    await expectAsync(outbox.enqueueSolicitacao(fila('p2'), pedido('p2'), 'id-servidor'))
      .toBeRejectedWithError(CorrecaoRecusadaError);

    const original = await outbox.enqueueSolicitacao(fila('p1'), pedido('p1'));
    const novo = await outbox.enqueueSolicitacao(fila('p3'), pedido('p3'), 'p1', original);
    expect((await outbox.getPendingItems()).map(i => i.id)).toEqual([novo]);
  });

  describe('filas deixadas por versões anteriores do app', () => {
    function substituida(id: string, tentativas: number): OutboxItem {
      return {
        id, ...intencao(id), status: 'PENDENTE', receiptStatus: 'CONFLICT', tentativas, criadoEm: new Date(),
        erroDetalhes: 'Intenção substituída por uma nova versão local.',
      };
    }

    it('versão substituída nunca é enviada', async () => {
      await db.outbox.add(substituida('velha', 0));
      expect(await outbox.getPendingItems()).toEqual([]);
      expect(await outbox.reivindicarParaEnvio('velha')).toBeUndefined();
    });

    it('versão substituída que nunca foi tentada sai da fila', async () => {
      await db.outbox.add(substituida('velha', 0));
      await outbox.migrateLegacyItems();
      expect(await db.outbox.get('velha')).toBeUndefined();
    });

    it('versão substituída que já foi tentada fica retida para o administrativo conferir', async () => {
      await db.outbox.add(substituida('tentada', 1));
      await outbox.migrateLegacyItems();
      const item = await db.outbox.get('tentada');
      expect(item?.status).toBe('ERRO');
      expect(item?.erroDetalhes).toContain('duplicado');
    });
  });
});
