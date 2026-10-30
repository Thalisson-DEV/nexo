import { estadoEnvioExecucao, descreverItemFila, tipoDoItem } from './estado-envio';
import { OutboxItem } from '../db/models';

describe('estado de envio (vocabulário do Nexo)', () => {
  describe('estadoEnvioExecucao', () => {
    const casos: Array<[string, any, any, boolean, string]> = [
      ['sincronizada → enviado', { statusSync: 'SINCRONIZADO' }, undefined, false, 'synced'],
      ['local sem fila → salvo no aparelho', { statusSync: 'LOCAL' }, undefined, false, 'committed'],
      ['local na fila → na fila', { statusSync: 'LOCAL' }, { status: 'PENDENTE' }, false, 'queued'],
      ['local na fila enviando agora → enviando', { statusSync: 'LOCAL' }, { status: 'PENDENTE' }, true, 'syncing'],
      ['resultado incerto → conflito', { statusSync: 'RESULTADO_INCERTO' }, undefined, false, 'conflict'],
      ['fila incerta → conflito', { statusSync: 'LOCAL' }, { status: 'RESULTADO_INCERTO' }, false, 'conflict'],
      ['erro → não enviado', { statusSync: 'ERRO' }, undefined, false, 'error'],
      ['backoffice → não enviado (em análise)', { statusSync: 'RECEBIDO_BACKOFFICE' }, undefined, false, 'error'],
      ['fila com erro → não enviado', { statusSync: 'LOCAL' }, { status: 'ERRO' }, false, 'error'],
    ];
    for (const [nome, exec, fila, enviando, esperado] of casos) {
      it(nome, () => expect(estadoEnvioExecucao(exec, fila, enviando)).toBe(esperado as any));
    }

    it('nunca diz "enviado" para item que está só na fila', () => {
      for (const enviando of [true, false]) {
        expect(estadoEnvioExecucao({ statusSync: 'LOCAL' }, { status: 'PENDENTE' } as any, enviando)).not.toBe('synced');
      }
    });
  });

  describe('descreverItemFila', () => {
    const agora = new Date(2026, 9, 8, 15, 0);
    function item(over: Partial<OutboxItem>): OutboxItem {
      return {
        id: 'i-1', endpoint: '/_execucoes-comerciais/lancar', method: 'POST',
        payload: { tempId: 't', body: { referenciaNota: '7004491' } },
        status: 'PENDENTE', tentativas: 0, criadoEm: new Date(2026, 9, 8, 11, 58), ...over,
      };
    }

    it('execução mostra a nota e a hora', () => {
      const d = descreverItemFila(item({}), false, agora);
      expect(d.titulo).toBe('Nota 7004491');
      expect(d.detalhe).toBe('Execução · 11:58');
      expect(d.estado).toBe('queued');
    });

    it('item de outro dia mostra a data', () => {
      expect(descreverItemFila(item({ criadoEm: new Date(2026, 9, 6, 9, 5) }), false, agora).detalhe).toBe('Execução · 06/10 09:05');
    });

    it('resultado incerto vira conflito com orientação', () => {
      const d = descreverItemFila(item({ status: 'RESULTADO_INCERTO' }), false, agora);
      expect(d.estado).toBe('conflict');
      expect(d.motivo).toContain('Confira');
    });

    it('erro recebido pelo escritório fala em análise, não em falha', () => {
      const d = descreverItemFila(item({ status: 'ERRO', receiptStatus: 'RECEIVED' }), false, agora);
      expect(d.estado).toBe('error');
      expect(d.motivo).toBe('Chegou ao escritório e está em análise.');
    });

    it('adiado por espera crescente avisa que tenta sozinho', () => {
      const d = descreverItemFila(item({ tentativas: 2, proximaTentativaEm: agora.getTime() + 60_000 }), false, agora);
      expect(d.motivo).toContain('Tenta de novo sozinho');
    });

    it('identifica o tipo pelo endpoint', () => {
      expect(tipoDoItem('/v1/solicitacoes-material')).toBe('pedido');
      expect(tipoDoItem('/v1/movimentacoes-estoque/transferencia')).toBe('transferencia');
      expect(tipoDoItem('/_orchestrator/inspecao/anexo')).toBe('anexo');
      expect(tipoDoItem('/_orchestrator/inspecao/lancar')).toBe('inspecao');
      expect(tipoDoItem('/v1/turnos/abrir')).toBe('turno');
      expect(descreverItemFila(item({ endpoint: '/v1/solicitacoes-material', payload: {} }), false, agora).titulo).toBe('Pedido de material');
    });
  });
});
