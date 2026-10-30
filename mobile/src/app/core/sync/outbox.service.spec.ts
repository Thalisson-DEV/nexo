import { TestBed } from '@angular/core/testing';
import { OutboxService } from './outbox.service';
import { db } from '../db/app-db';
import { InspecaoLocal, OutboxItem } from '../db/models';

describe('OutboxService (Offline Queue)', () => {
  const attachment413RecoveryKey = 'outbox:recover-inspecao-anexo-413:v1';
  let service: OutboxService;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [OutboxService]
    });
    service = TestBed.inject(OutboxService);
    
    // Limpa as tabelas envolvidas antes de cada teste
    await Promise.all([
      db.outbox.clear(),
      db.inspecoes.clear(),
      db.config.delete(attachment413RecoveryKey)
    ]);
    // Força a atualização dos signals para zero
    await (service as any).updatePendingCount();
  });

  afterAll(async () => {
    await Promise.all([
      db.outbox.clear(),
      db.inspecoes.clear(),
      db.config.delete(attachment413RecoveryKey)
    ]);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('enqueue()', () => {
    it('should add a new item with PENDENTE status and initial properties', async () => {
      const payload = { foo: 'bar' };
      const id = await service.enqueue({
        endpoint: '/api/v1/test',
        method: 'POST',
        payload
      });

      expect(id).toBeDefined();

      const item = await service.getById(id);
      expect(item).toBeTruthy();
      expect(item?.endpoint).toBe('/api/v1/test');
      expect(item?.method).toBe('POST');
      expect(item?.payload).toEqual(payload);
      expect(item?.status).toBe('PENDENTE');
      expect(item?.tentativas).toBe(0);
      expect(item?.criadoEm).toBeDefined();
    });

    it('should update the pendingCount signal when enqueuing', async () => {
      expect(service.pendingCount()).toBe(0);

      await service.enqueue({ endpoint: '/api/v1/test', method: 'POST', payload: {} });
      expect(service.pendingCount()).toBe(1);

      await service.enqueue({ endpoint: '/api/v1/test2', method: 'POST', payload: {} });
      expect(service.pendingCount()).toBe(2);
    });
  });

  describe('enqueueInspecao()', () => {
    it('substitui o rascunho e grava inspeção e outbox com o mesmo id atomicamente', async () => {
      await db.inspecoes.add({
        id: 'draft-1',
        idFormulario: 'form-1',
        status: 'EM_ANDAMENTO',
        statusSync: 'PENDENTE',
        dataRealizacao: '2026-08-01T10:00:00-03:00'
      });

      const inspecao = {
        idFormulario: 'form-1',
        idProcesso: 'processo-1',
        status: 'CONCLUIDA',
        statusSync: 'PENDENTE',
        dataRealizacao: '2026-08-01T10:05:00-03:00'
      } satisfies Omit<InspecaoLocal, 'id'>;
      const payload = { request: { idFormulario: 'form-1' }, anexosPendentes: [] };

      const id = await service.enqueueInspecao({
        endpoint: '/_orchestrator/inspecao/lancar',
        method: 'POST',
        payload
      }, inspecao, 'draft-1');

      const [item, registro, rascunho] = await Promise.all([
        db.outbox.get(id),
        db.inspecoes.get(id),
        db.inspecoes.get('draft-1')
      ]);

      expect(item).toEqual(jasmine.objectContaining({
        id,
        status: 'PENDENTE',
        tentativas: 0,
        payload,
        originalPayload: payload,
        receiptStatus: 'QUEUED'
      }));
      expect(registro).toEqual(jasmine.objectContaining({ id, status: 'CONCLUIDA' }));
      expect(rascunho).toBeUndefined();
      expect(service.pendingCount()).toBe(1);
    });
  });

  describe('getPendingItems() & getErrorItems()', () => {
    it('should fetch only PENDENTE items ordered by creation date', async () => {
      // Inserção manual para simular ordem
      const baseItem = { endpoint: '', method: 'POST' as const, payload: {}, tentativas: 0 };
      await db.outbox.bulkAdd([
        { ...baseItem, id: 'out-1', status: 'PENDENTE', criadoEm: new Date('2023-01-02T10:00:00Z') },
        { ...baseItem, id: 'out-2', status: 'SINCRONIZADO', criadoEm: new Date('2023-01-01T10:00:00Z') },
        { ...baseItem, id: 'out-3', status: 'PENDENTE', criadoEm: new Date('2023-01-01T08:00:00Z') } // Mais antigo
      ]);

      const pendentes = await service.getPendingItems();

      expect(pendentes.length).toBe(2);
      // Deve vir ordenado pela data de criação
      expect(pendentes[0].id).toBe('out-3');
      expect(pendentes[1].id).toBe('out-1');
    });

    it('reativa uma unica vez anexos bloqueados pelo antigo limite HTTP 413', async () => {
      const baseItem = {
        endpoint: '/_orchestrator/inspecao/anexo',
        method: 'POST' as const,
        payload: {},
        tentativas: 1,
        criadoEm: new Date()
      };
      await db.outbox.bulkAdd([
        {
          ...baseItem,
          id: 'anexo-413',
          status: 'ERRO',
          erroDetalhes: 'Http failure response: 413 Request Entity Too Large'
        },
        {
          ...baseItem,
          id: 'anexo-403',
          status: 'ERRO',
          erroDetalhes: 'Http failure response: 403 Forbidden'
        }
      ]);

      const pendentes = await service.getPendingItems();
      const [recuperado, naoRecuperado, marcador] = await Promise.all([
        db.outbox.get('anexo-413'),
        db.outbox.get('anexo-403'),
        db.config.get(attachment413RecoveryKey)
      ]);

      expect(pendentes.map(item => item.id)).toContain('anexo-413');
      expect(recuperado).toEqual(jasmine.objectContaining({
        status: 'PENDENTE',
        tentativas: 0
      }));
      expect(recuperado?.erroDetalhes).toBeUndefined();
      expect(naoRecuperado?.status).toBe('ERRO');
      expect(marcador?.value).toBeTrue();
    });
  });

  describe('updateStatus()', () => {
    it('should update item status and recalculate signals', async () => {
      const id = await service.enqueue({ endpoint: '/api/v1/test', method: 'POST', payload: {} });
      expect(service.pendingCount()).toBe(1);
      expect(service.errorCount()).toBe(0);

      // Passando para ERRO
      await service.updateStatus(id, 'ERRO', 'Faltou conexão');
      
      const erroItem = await service.getById(id);
      expect(erroItem?.status).toBe('ERRO');
      expect(erroItem?.erroDetalhes).toBe('Faltou conexão');
      expect(service.pendingCount()).toBe(1); // ERRO também conta como pendente
      expect(service.errorCount()).toBe(1);

      // Passando para SINCRONIZADO
      await service.updateStatus(id, 'SINCRONIZADO');
      
      const syncItem = await service.getById(id);
      expect(syncItem?.status).toBe('SINCRONIZADO');
      expect(service.pendingCount()).toBe(0); // Sincronizado não é mais pendente
      expect(service.errorCount()).toBe(0);
    });
  });

  describe('incrementAttempt()', () => {
    it('should increment attempt counter by 1', async () => {
      const id = await service.enqueue({ endpoint: '/test', method: 'POST', payload: {} });
      
      const attempt1 = await service.incrementAttempt(id);
      expect(attempt1).toBe(1);

      const attempt2 = await service.incrementAttempt(id);
      expect(attempt2).toBe(2);

      const item = await service.getById(id);
      expect(item?.tentativas).toBe(2);
    });
  });

  describe('Funções canDiscard(), canEdit() e discard()', () => {
    it('deve permitir descarte e edição de item local não tentado', async () => {
      const id = await service.enqueue({
        endpoint: '/api/test',
        method: 'POST',
        payload: { a: 1 }
      });
      const item = await service.getById(id);

      expect(service.canDiscard(item)).toBeTrue();
      expect(service.canEdit(item)).toBeTrue();
      expect(service.isEmTratamentoBackoffice(item)).toBeFalse();
      expect(service.isResultadoIncerto(item)).toBeFalse();

      const discarded = await service.discard(id);
      expect(discarded).toBeTrue();
      const after = await service.getById(id);
      expect(after).toBeUndefined();
      expect(service.pendingCount()).toBe(0);
    });

    it('não deve permitir descarte ou edição de item em erro ou após tentativa', async () => {
      const id = await service.enqueue({
        endpoint: '/api/test',
        method: 'POST',
        payload: { a: 1 }
      });
      await service.updateStatus(id, 'ERRO', 'Falha no servidor');
      await service.incrementAttempt(id);
      const item = await service.getById(id);

      expect(service.canDiscard(item)).toBeFalse();
      expect(service.canEdit(item)).toBeFalse();
      expect(service.isEmTratamentoBackoffice(item)).toBeTrue();

      const discarded = await service.discard(id);
      expect(discarded).toBeFalse();
      const preserved = await service.getById(id);
      expect(preserved).toBeDefined();
      expect(preserved?.status).toBe('ERRO');
      expect(service.pendingCount()).toBe(1);
    });

    it('não deve permitir descarte de item com resultado incerto', async () => {
      const id = await service.enqueue({
        endpoint: '/api/test',
        method: 'POST',
        payload: { a: 1 }
      });
      await service.updateStatus(id, 'RESULTADO_INCERTO');
      const item = await service.getById(id);

      expect(service.canDiscard(item)).toBeFalse();
      expect(service.canEdit(item)).toBeFalse();
      expect(service.isResultadoIncerto(item)).toBeTrue();

      const discarded = await service.discard(id);
      expect(discarded).toBeFalse();
    });

    it('não deve permitir descarte ou edição quando item já foi recebido pelo backoffice', async () => {
      const id = await service.enqueue({
        endpoint: '/api/test',
        method: 'POST',
        payload: { a: 1 }
      });
      await service.markServerReceipt(id, {
        operationId: 'op-123',
        status: 'RECEIVED'
      });
      const item = await service.getById(id);

      expect(service.canDiscard(item)).toBeFalse();
      expect(service.canEdit(item)).toBeFalse();
      expect(service.isEmTratamentoBackoffice(item)).toBeTrue();

      const discarded = await service.discard(id);
      expect(discarded).toBeFalse();
    });

    it('should remove all items with status SINCRONIZADO', async () => {
      const baseItem = { endpoint: '', method: 'POST' as const, payload: {}, tentativas: 0, criadoEm: new Date() };
      
      await db.outbox.bulkAdd([
        { ...baseItem, id: 'out-pendente', status: 'PENDENTE' },
        { ...baseItem, id: 'out-sync1', status: 'SINCRONIZADO' },
        { ...baseItem, id: 'out-sync2', status: 'SINCRONIZADO' }
      ]);
      
      await service.clearSyncedItems();
      
      const items = await db.outbox.toArray();
      
      expect(items.length).toBe(1);
      expect(items[0].id).toBe('out-pendente');
      expect(items[0].status).toBe('PENDENTE');
    });
  });
});
