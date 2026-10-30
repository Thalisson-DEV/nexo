import { TestBed } from '@angular/core/testing';
import { OutboxService, calcularEspera, ESPERA_BASE_MS, ESPERA_MAX_MS } from './outbox.service';
import { db } from '../db/app-db';

describe('fila de envio: espera crescente entre tentativas', () => {
  describe('calcularEspera', () => {
    it('dobra a cada tentativa, começando em 5 s (sem variação no meio da faixa)', () => {
      expect([1, 2, 3, 4, 5].map(n => calcularEspera(n, 0.5))).toEqual([5_000, 10_000, 20_000, 40_000, 80_000]);
    });

    it('nunca passa de 5 minutos', () => {
      expect(calcularEspera(30, 0.5)).toBe(ESPERA_MAX_MS);
      expect(calcularEspera(30, 1)).toBe(ESPERA_MAX_MS * 1.25);
    });

    it('variação de ±25% para os aparelhos não voltarem todos juntos', () => {
      expect(calcularEspera(1, 0)).toBe(ESPERA_BASE_MS * 0.75);
      expect(calcularEspera(1, 1)).toBe(ESPERA_BASE_MS * 1.25);
    });

    it('entradas estranhas não geram espera negativa nem infinita', () => {
      for (const [n, r] of [[0, 0.5], [-3, 0.5], [1, -1], [1, 9]] as const) {
        const ms = calcularEspera(n, r);
        expect(ms).toBeGreaterThanOrEqual(ESPERA_BASE_MS * 0.75);
        expect(ms).toBeLessThanOrEqual(ESPERA_BASE_MS * 1.25);
      }
    });
  });

  describe('OutboxService (IndexedDB real)', () => {
    let outbox: OutboxService;

    beforeEach(async () => {
      await db.outbox.clear();
      TestBed.configureTestingModule({});
      outbox = TestBed.inject(OutboxService);
    });

    afterEach(async () => {
      await db.outbox.clear();
    });

    it('item adiado não é reenviado antes do prazo e volta depois dele', async () => {
      const id = await outbox.enqueue({ endpoint: '/v1/x', method: 'POST', payload: { a: 1 } });
      await outbox.adiarNovaTentativa(id, 1, Date.now(), 0.5);
      expect((await outbox.getPendingItems()).map(i => i.id)).not.toContain(id);
      expect(await outbox.proximoReenvioAgendado()).toBeGreaterThan(Date.now());

      await db.outbox.update(id, { proximaTentativaEm: Date.now() - 1 });
      expect((await outbox.getPendingItems()).map(i => i.id)).toContain(id);
    });

    it('item adiado continua contando como pendente para o usuário', async () => {
      const id = await outbox.enqueue({ endpoint: '/v1/x', method: 'POST', payload: {} });
      await outbox.adiarNovaTentativa(id, 3);
      await outbox.updateStatus(id, 'PENDENTE');
      expect(outbox.pendingCount()).toBe(1);
    });

    it('enviado com sucesso limpa o adiamento', async () => {
      const id = await outbox.enqueue({ endpoint: '/v1/x', method: 'POST', payload: {} });
      await outbox.adiarNovaTentativa(id, 2);
      await outbox.updateStatus(id, 'SINCRONIZADO');
      expect((await db.outbox.get(id))?.proximaTentativaEm).toBeUndefined();
    });
  });

  describe('trava entre abas (Web Locks)', () => {
    it('enquanto uma aba segura a trava da fila, outra não consegue processar ao mesmo tempo', async () => {
      let liberar!: () => void;
      const segurando = navigator.locks.request('ces-outbox', () => new Promise<void>(r => (liberar = r)));
      const tentativa = await navigator.locks.request('ces-outbox', { ifAvailable: true }, lock => lock !== null);
      expect(tentativa).toBeFalse();
      liberar();
      await segurando;
      expect(await navigator.locks.request('ces-outbox', { ifAvailable: true }, lock => lock !== null)).toBeTrue();
    });
  });
});
