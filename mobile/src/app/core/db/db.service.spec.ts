import { TestBed } from '@angular/core/testing';
import { DbService } from './db.service';
import { db } from './app-db';

describe('DbService (IndexedDB)', () => {
  let service: DbService;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [DbService]
    });
    service = TestBed.inject(DbService);
    
    // Limpa o banco de dados antes de cada teste para garantir isolamento
    await service.clearAllData();
  });

  afterAll(async () => {
    // Limpa a base ao final da suíte
    await db.transaction('rw', db.tables, async () => {
      await Promise.all(db.tables.map(table => table.clear()));
    });
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('impede troca de conta com operações pendentes e preserva os dados', async () => {
    await db.outbox.put({
      id: 'pendente', endpoint: '/v1/solicitacoes-material', method: 'POST', payload: {},
      status: 'PENDENTE', tentativas: 0, criadoEm: new Date()
    });

    await expectAsync(service.prepareForLogin('usuario-novo', 'usuario-antigo'))
      .toBeRejectedWithError(/operações pendentes/);
    expect(await db.outbox.get('pendente')).toBeDefined();
  });

  it('limpa dados da conta anterior quando não há trabalho pendente', async () => {
    await service.saveEquipe({ id: 'equipe-antiga', codigo: 'ANTIGA' } as any);

    await service.prepareForLogin('usuario-novo', 'usuario-antigo');

    expect(await service.getEquipe()).toBeUndefined();
  });

  describe('Equipe', () => {
    it('should save and get Equipe', async () => {
      const mockEquipe = { id: 'eq-1', codigo: 'ABC-123', nomeTipoEquipe: 'Linha Viva' };
      
      await service.saveEquipe(mockEquipe as any);
      const result = await service.getEquipe();
      
      expect(result).toEqual(mockEquipe as any);
    });
  });

  describe('Colaboradores', () => {
    it('should replace Colaboradores atomically', async () => {
      // Setup inicial
      const initial = [{ id: 'col-1', nomeCompleto: 'João', ativo: true }];
      await service.bulkSaveColaboradores(initial as any);
      
      // Valida inserção inicial
      let atuais = await service.getColaboradoresAtivos();
      expect(atuais.length).toBe(1);

      // Substituição atômica
      const novos = [{ id: 'col-2', nomeCompleto: 'Maria', ativo: true }];
      await service.replaceColaboradores(novos as any);
      
      // Valida substituição
      atuais = await service.getColaboradoresAtivos();
      expect(atuais.length).toBe(1);
      expect(atuais[0].id).toBe('col-2');
      expect(atuais[0].nomeCompleto).toBe('Maria');
    });
  });

  describe('Saldos e Seriais', () => {
    it('should deduct local balance and remove series correctly', async () => {
      // 1. Prepara Saldo e Serial inicial
      await service.bulkSaveSaldos([{ 
        id: 's-1', idEstoque: 'est-1', idMaterial: 'mat-1', 
        condicao: 'NOVO', quantidade: 5, ativo: true 
      }]);
      
      await service.bulkSaveSaldosSeriais([{ 
        id: 'ss-1', idEstoque: 'est-1', idMaterial: 'mat-1', 
        condicao: 'NOVO', numSerie: 'SERIE-XYZ', ativo: true 
      }]);
      
      // 2. Executa a dedução (ex: consumido em campo)
      await service.deduzirSaldoLocal('est-1', 'mat-1', 2, 'NOVO', ['SERIE-XYZ']);
      
      // 3. Valida Saldo Quantitativo
      const saldos = await service.getSaldos();
      expect(saldos.length).toBe(1);
      expect(saldos[0].quantidade).toBe(3); // 5 - 2 = 3
      
      // 4. Valida Remoção da Série
      const seriais = await service.getSeriaisPorMaterial('est-1', 'mat-1', 'NOVO');
      expect(seriais.length).toBe(0); // A série foi consumida e removida
    });
  });

  describe('Transferência Offline', () => {
    it('should deduct balance, create local transfer and enqueue outbox atomically', async () => {
      // Popula saldo inicial para dedução
      await service.bulkSaveSaldos([{ 
        id: 's-2', idEstoque: 'est-ORIGEM', idMaterial: 'mat-99', 
        condicao: 'NOVO', quantidade: 10, ativo: true 
      }]);

      const itensTransferencia = [
        { idMaterial: 'mat-99', quantidade: 4, numSerie: null as unknown as string, condicao: 'NOVO' as const }
      ];

      // Ação
      await service.registrarTransferenciaOffline(
        'est-ORIGEM',
        'est-DESTINO',
        itensTransferencia,
        'data:image/png;base64,fake-entregador',
        'data:image/png;base64,fake-recebedor'
      );

      // Validação 1: O Saldo reduziu?
      const saldos = await service.getSaldos();
      expect(saldos[0].quantidade).toBe(6); // 10 - 4 = 6

      // Validação 2: A transferência local existe?
      const transferencias = await service.getTransferencias();
      expect(transferencias.length).toBe(1);
      expect(transferencias[0].idEstoqueOrigem).toBe('est-ORIGEM');
      expect(transferencias[0].idEstoqueDestino).toBe('est-DESTINO');
      expect(transferencias[0].assinaturaEntregador).toBe('data:image/png;base64,fake-entregador');
      expect(transferencias[0].assinaturaRecebedor).toBe('data:image/png;base64,fake-recebedor');
      expect(transferencias[0].statusSync).toBe('PENDENTE');

      // Validação 3: O item foi pro Outbox?
      const outbox = await service.getPendingOutbox();
      expect(outbox.length).toBe(1);
      expect(outbox[0].endpoint).toBe('/v1/movimentacoes-estoque/transferencia');
      expect(outbox[0].method).toBe('POST');
      expect(outbox[0].status).toBe('PENDENTE');
      expect(outbox[0].payload.itens).toEqual(itensTransferencia);
      expect(outbox[0].payload.assinaturaEntregador).toBe('data:image/png;base64,fake-entregador');
      expect(outbox[0].payload.assinaturaRecebedor).toBe('data:image/png;base64,fake-recebedor');
    });
  });

  describe('Execuções vindas do servidor (replaceExecucoesSincronizadas)', () => {
    const lancadaEm = '2026-10-22T17:15:39.494Z';
    const temporaria = (id = 'temp-1') => ({
      id, referenciaNota: '234567890', idTurno: 't-1', idEquipe: 'eq-1', dataLancamento: lancadaEm,
      statusExecucao: 'CONCLUIDO', acoes: ['a1'], materiais: [], statusSync: 'LOCAL'
    } as any);
    const doServidor = { ...temporaria('real-1'), statusSync: 'SINCRONIZADO' } as any;
    const filaDe = (status: string, receiptStatus: string) => ({
      id: 'fila-1', endpoint: '/_execucoes-comerciais/lancar', method: 'POST', payload: { tempId: 'temp-1' },
      status, receiptStatus, tentativas: 0, criadoEm: new Date()
    } as any);

    it('lançamento resolvido pelo escritório não fica duplicado no aparelho', async () => {
      await db.execucoesCampo.put(temporaria());
      await db.outbox.put(filaDe('SINCRONIZADO', 'RESOLVED'));

      await service.replaceExecucoesSincronizadas([doServidor]);

      const ids = (await db.execucoesCampo.toArray()).map(e => e.id);
      expect(ids).toEqual(['real-1']);
      expect(await db.outbox.get('fila-1')).toBeUndefined();
    });

    it('lançamento ainda na fila nunca é apagado, mesmo parecido com uma nota do servidor', async () => {
      await db.execucoesCampo.put(temporaria());
      await db.outbox.put(filaDe('PENDENTE', 'QUEUED'));

      await service.replaceExecucoesSincronizadas([doServidor]);

      const ids = (await db.execucoesCampo.toArray()).map(e => e.id).sort();
      expect(ids).toEqual(['real-1', 'temp-1']);
      expect(await db.outbox.get('fila-1')).toBeDefined();
    });

    it('mesmo número em outro horário é outra nota', async () => {
      await db.execucoesCampo.put(temporaria());
      await db.outbox.put(filaDe('SINCRONIZADO', 'RESOLVED'));

      await service.replaceExecucoesSincronizadas([{ ...doServidor, dataLancamento: '2026-10-21T16:42:33.939Z' }]);

      expect((await db.execucoesCampo.toArray()).map(e => e.id).sort()).toEqual(['real-1', 'temp-1']);
    });
  });

  describe('Substituição Inteligente de Solicitações (replaceSolicitacoesSincronizadas)', () => {
    it('should delete local-only synced items but preserve offline pendings', async () => {
      const hoje = new Date().toISOString();

      // Simula uma solicitação que está PENDENTE no outbox (criada offline)
      const offlineId = 'sol-offline';
      await service.saveSolicitacao({ id: offlineId, status: 'PENDENTE', dataSolicitacao: hoje } as any);
      await service.enqueueOutbox({
        id: 'out-88',
        endpoint: '/v1/solicitacoes',
        payload: { tempId: offlineId },
        status: 'PENDENTE',
        method: 'POST',
        tentativas: 0,
        criadoEm: new Date()
      });

      // Simula uma solicitação velha que já estava sincronizada no BD local,
      // mas que sumiu do servidor (ex: foi excluída ou mudou de filtro)
      await service.saveSolicitacao({ id: 'sol-velha', status: 'APROVADO', dataSolicitacao: hoje } as any);

      // Simula o retorno do servidor com UMA NOVA solicitação (que nunca vimos)
      const novasSincronizadas = [
        { id: 'sol-nova-servidor', status: 'ATENDIDO', dataSolicitacao: hoje }
      ];

      // Executa a substituição inteligente
      await service.replaceSolicitacoesSincronizadas(novasSincronizadas as any);

      const todasLocal = await service.getSolicitacoes();
      
      // Validações:
      expect(todasLocal.length).toBe(2);
      
      // A velha sumiu?
      expect(todasLocal.find(s => s.id === 'sol-velha')).toBeUndefined();
      
      // A nova do servidor entrou?
      expect(todasLocal.find(s => s.id === 'sol-nova-servidor')).toBeTruthy();
      
      // A pendente offline sobreviveu à limpeza atômica?
      expect(todasLocal.find(s => s.id === offlineId)).toBeTruthy();
    });
  });

  describe('Substituição Inteligente de Transferências (replaceTransferenciasSincronizadas)', () => {
    it('deve preservar assinaturas digitais coletadas localmente ao sincronizar com servidor', async () => {
      const hoje = new Date().toISOString();
      const transfId = 'transf-100';

      // Salva uma transferência local que já possui assinatura coletada em base64
      await service.saveTransferencia({
        id: transfId,
        idEstoqueOrigem: 'est-ORIGEM',
        idEstoqueDestino: 'est-DESTINO',
        dataTransferencia: hoje,
        statusSync: 'SINCRONIZADO',
        itens: [{ idMaterial: 'mat-1', quantidade: 5, condicao: 'NOVO', numSerie: null }],
        assinaturaEntregador: 'data:image/png;base64,assinatura-local-entregador',
        assinaturaRecebedor: 'data:image/png;base64,assinatura-local-recebedor'
      });

      // O servidor retorna a transferência sincronizada, mas sem as imagens base64 brutas (ou com 'DIGITAL')
      const doServidor = [{
        id: transfId,
        idEstoqueOrigem: 'est-ORIGEM',
        idEstoqueDestino: 'est-DESTINO',
        dataTransferencia: hoje,
        statusSync: 'SINCRONIZADO' as const,
        itens: [{ idMaterial: 'mat-1', quantidade: 5, condicao: 'NOVO' as const, numSerie: null }],
        assinaturaEntregador: 'DIGITAL',
        assinaturaRecebedor: 'DIGITAL'
      }];

      await service.replaceTransferenciasSincronizadas(doServidor);

      const resultado = await service.getTransferenciaById(transfId);
      expect(resultado).toBeDefined();
      expect(resultado?.assinaturaEntregador).toBe('data:image/png;base64,assinatura-local-entregador');
      expect(resultado?.assinaturaRecebedor).toBe('data:image/png;base64,assinatura-local-recebedor');
    });
  });
});
