import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ExecucoesService, NovaExecucaoPayload, EXECUCAO_LANCAR_ENDPOINT } from './execucoes.service';
import { DbService } from '../db/db.service';
import { OutboxService } from '../sync/outbox.service';
import { TurnoService } from '../turno/turno.service';
import { TokenService } from '../auth/token.service';
import { TurnoLocal, ExecucaoCampoLocal } from '../db/models';

describe('ExecucoesService', () => {
  let service: ExecucoesService;
  let dbSpy: jasmine.SpyObj<DbService>;
  let outboxSpy: jasmine.SpyObj<OutboxService>;
  let turnoSpy: jasmine.SpyObj<TurnoService>;
  let tokenSpy: jasmine.SpyObj<TokenService>;

  beforeEach(() => {
    dbSpy = jasmine.createSpyObj('DbService', [
      'getOutboxItemByExecucaoId', 'deleteExecucao', 'saveExecucao', 
      'deduzirSaldoLocal', 'getExecucaoById', 'getExecucoes', 'getTransferencias',
      'getAcoes'
    ]);
    dbSpy.getAcoes.and.resolveTo([]);
    outboxSpy = jasmine.createSpyObj('OutboxService', ['discard', 'enqueue', 'enqueueExecucao']);
    turnoSpy = jasmine.createSpyObj('TurnoService', ['turnoAtivo']);
    tokenSpy = jasmine.createSpyObj('TokenService', ['equipeId', 'estoqueId']);

    TestBed.configureTestingModule({
      providers: [
        ExecucoesService,
        { provide: DbService, useValue: dbSpy },
        { provide: OutboxService, useValue: outboxSpy },
        { provide: TurnoService, useValue: turnoSpy },
        { provide: TokenService, useValue: tokenSpy }
      ]
    });

    service = TestBed.inject(ExecucoesService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('salvarExecucao()', () => {
    const payload: NovaExecucaoPayload = {
      referenciaNota: 'N-123',
      tipoNota: 'COMUM',
      tipoServico: { id: 'ts-cl', codigo: 'CL', descricao: 'LIGAÇÃO NOVA' },
      latitude: -12.97,
      longitude: -38.5,
      acoes: ['acao-1'],
      materiais: [
        { idMaterial: 'mat-1', quantidade: 2, operacao: 'APLICADO', numSerie: 'S-1', condicao: 'NOVO' }
      ]
    };

    it('should throw error if no active turno', async () => {
      turnoSpy.turnoAtivo.and.returnValue(null);
      await expectAsync(service.salvarExecucao(payload)).toBeRejectedWithError(/Não há turno aberto/);
    });

    it('recusa lançamento sem tipo de serviço', async () => {
      turnoSpy.turnoAtivo.and.returnValue({ id: 'turno-1' } as TurnoLocal);
      tokenSpy.equipeId.and.returnValue('eq-1');
      const semTipo = { ...payload, tipoServico: undefined } as unknown as NovaExecucaoPayload;
      await expectAsync(service.salvarExecucao(semTipo)).toBeRejectedWithError(/tipo de serviço/);
      expect(outboxSpy.enqueueExecucao).not.toHaveBeenCalled();
    });

    it('should throw error if no active equipe', async () => {
      turnoSpy.turnoAtivo.and.returnValue({ id: 'turno-1' } as TurnoLocal);
      tokenSpy.equipeId.and.returnValue(undefined);
      await expectAsync(service.salvarExecucao(payload)).toBeRejectedWithError(/Equipe não identificada/);
    });

    // O lançamento passou a ser em etapa única: a execução já nasce CONCLUIDO e
    // vai direto para o outbox, sem o passo intermediário de "INICIADO".
    it('should save execucao local as CONCLUIDO and deduct local stock if APLICADO', async () => {
      turnoSpy.turnoAtivo.and.returnValue({ id: 'turno-1' } as TurnoLocal);
      tokenSpy.equipeId.and.returnValue('eq-1');
      tokenSpy.estoqueId.and.returnValue('est-1');

      const id = await service.salvarExecucao(payload);

      expect(id).toBeDefined();
      expect(outboxSpy.enqueueExecucao).toHaveBeenCalledWith(jasmine.anything(), jasmine.objectContaining({
        referenciaNota: 'N-123', statusExecucao: 'CONCLUIDO', statusSync: 'LOCAL', idTurno: 'turno-1',
        latitude: -12.97, longitude: -38.5, idMunicipio: null,
        tipoServico: { id: 'ts-cl', codigo: 'CL', descricao: 'LIGAÇÃO NOVA' }
      }), undefined, undefined);
      const intencao = outboxSpy.enqueueExecucao.calls.mostRecent().args[0] as { payload: { body: { idTipoServico: string } } };
      expect(intencao.payload.body.idTipoServico).toBe('ts-cl');
      expect(dbSpy.deduzirSaldoLocal).toHaveBeenCalledWith('est-1', 'mat-1', 2, 'NOVO', ['S-1']);
      expect(outboxSpy.enqueueExecucao).toHaveBeenCalled();
    });

    it('should supersede old outbox and preserve its audit record when editing', async () => {
      turnoSpy.turnoAtivo.and.returnValue({ id: 'turno-1' } as TurnoLocal);
      tokenSpy.equipeId.and.returnValue('eq-1');
      dbSpy.getOutboxItemByExecucaoId.and.resolveTo({ id: 'out-1' } as any);

      const newId = await service.salvarExecucao(payload, { editId: 'old-exec' });

      expect(newId).not.toBe('old-exec');
      expect(outboxSpy.discard).not.toHaveBeenCalled();
      expect(dbSpy.deleteExecucao).not.toHaveBeenCalled();
      expect(outboxSpy.enqueueExecucao).toHaveBeenCalledWith(jasmine.anything(), jasmine.anything(), 'old-exec', 'out-1');
    });

    it('na ocorrência, material da Concessionária não mexe no saldo do caminhão; o da equipe sim', async () => {
      turnoSpy.turnoAtivo.and.returnValue({ id: 'turno-1' } as TurnoLocal);
      tokenSpy.equipeId.and.returnValue('eq-1');
      tokenSpy.estoqueId.and.returnValue('est-1');

      await service.salvarExecucao({
        ...payload,
        referenciaNota: 'OC-12AB',
        tipoNota: 'OCORRENCIA',
        materiais: [
          { idMaterial: 'concessionaria', quantidade: 3, operacao: 'APLICADO', condicao: 'NOVO', origem: 'CONCESSIONARIA' },
          { idMaterial: 'concessionaria-ret', quantidade: 1, operacao: 'RETIRADO', condicao: 'SUCATA', origem: 'CONCESSIONARIA' },
          { idMaterial: 'proprio', quantidade: 2, operacao: 'APLICADO', condicao: 'NOVO', origem: 'EQUIPE' }
        ]
      });

      expect(dbSpy.deduzirSaldoLocal).toHaveBeenCalledTimes(1);
      expect(dbSpy.deduzirSaldoLocal).toHaveBeenCalledWith('est-1', 'proprio', 2, 'NOVO', undefined);
      const intencao = outboxSpy.enqueueExecucao.calls.mostRecent().args[0] as { payload: { body: { tipoNota: string; materiais: { origem: string }[] } } };
      expect(intencao.payload.body.tipoNota).toBe('OCORRENCIA');
      expect(intencao.payload.body.materiais.map(m => m.origem)).toEqual(['CONCESSIONARIA', 'CONCESSIONARIA', 'EQUIPE']);
    });

    it('nota avulsa guarda o número provisório e fica aguardando o número oficial', async () => {
      turnoSpy.turnoAtivo.and.returnValue({ id: 'turno-1' } as TurnoLocal);
      tokenSpy.equipeId.and.returnValue('eq-1');

      await service.salvarExecucao({ ...payload, referenciaNota: 'AV-EQ1-261019-01', tipoNota: 'AVULSA' });

      expect(outboxSpy.enqueueExecucao).toHaveBeenCalledWith(jasmine.anything(), jasmine.objectContaining({
        referenciaNota: 'AV-EQ1-261019-01', tipoNota: 'AVULSA', referenciaProvisoria: 'AV-EQ1-261019-01', aguardandoNumeroOficial: true
      }), undefined, undefined);
    });

    it('calcula o valorTotalFaturado da execução a partir dos valores das ações no banco local', async () => {
      turnoSpy.turnoAtivo.and.returnValue({ id: 'turno-1' } as TurnoLocal);
      tokenSpy.equipeId.and.returnValue('eq-1');
      dbSpy.getAcoes.and.resolveTo([
        { id: 'acao-1', codigo: 'A1', nome: 'Ação 1', ativo: true, valor: 150.5 },
        { id: 'acao-2', codigo: 'A2', nome: 'Ação 2', ativo: true, valor: 49.5 }
      ]);

      await service.salvarExecucao({
        ...payload,
        acoes: ['acao-1', 'acao-2']
      });

      expect(outboxSpy.enqueueExecucao).toHaveBeenCalledWith(
        jasmine.anything(),
        jasmine.objectContaining({
          valorTotalFaturado: 200
        }),
        undefined,
        undefined
      );
    });
  });

  describe('montarCorpo()', () => {
    it('envia a data de lançamento, o tipo de serviço e uma única localização, sem município', () => {
      const corpo = service.montarCorpo({
        id: 'x', referenciaNota: 'N-1', idTurno: 't', idEquipe: 'eq-1', idMunicipio: null,
        tipoServico: { id: 'ts-cs', codigo: 'CS', descricao: 'CORTE' },
        latitude: -12.9, longitude: -38.5, dataLancamento: '2026-10-03T10:00:00Z',
        statusExecucao: 'CONCLUIDO', acoes: ['a'],
        materiais: [{ idMaterial: 'm', quantidade: 1, operacao: 'RETIRADO', condicao: 'SUCATA' }],
        statusSync: 'LOCAL'
      });

      expect(corpo).toEqual({
        referenciaNota: 'N-1', tipoNota: 'COMUM', idEquipe: 'eq-1', idTurno: 't', idTipoServico: 'ts-cs', dataLancamento: '2026-10-03T10:00:00Z',
        latitude: -12.9, longitude: -38.5, acoes: ['a'],
        materiais: [{ idMaterial: 'm', quantidade: 1, operacao: 'RETIRADO', numSerie: null, origem: 'EQUIPE' }]
      });
      expect('idMunicipio' in corpo).toBeFalse();
      expect('dataInicio' in corpo).toBeFalse();
    });
  });

  describe('descartar()', () => {
    it('should discard outbox and execucao when discard is allowed', async () => {
      dbSpy.getOutboxItemByExecucaoId.and.resolveTo({ id: 'out-x' } as any);
      outboxSpy.discard.and.resolveTo(true);

      await service.descartar('exec-x');

      expect(outboxSpy.discard).toHaveBeenCalledWith('out-x');
      expect(dbSpy.deleteExecucao).toHaveBeenCalledWith('exec-x');
    });

    it('should not delete execucao if outbox discard is rejected', async () => {
      dbSpy.getOutboxItemByExecucaoId.and.resolveTo({ id: 'out-x' } as any);
      outboxSpy.discard.and.resolveTo(false);

      await expectAsync(service.descartar('exec-x')).toBeRejectedWithError(/Esta execução foi preservada e aguarda tratamento administrativo/);

      expect(outboxSpy.discard).toHaveBeenCalledWith('out-x');
      expect(dbSpy.deleteExecucao).not.toHaveBeenCalled();
    });
  });

  describe('reaplicarDeducoesPendentes()', () => {
    it('should reapply deductions for LOCAL executions and PENDENTE transfers', async () => {
      dbSpy.getExecucoes.and.resolveTo([
        { 
          id: 'e-1', statusSync: 'LOCAL',
          materiais: [{ idMaterial: 'm-1', operacao: 'APLICADO', quantidade: 5, condicao: 'NOVO' }]
        } as any,
        { id: 'e-2', statusSync: 'SINCRONIZADO', materiais: [] } as any
      ]);

      dbSpy.getTransferencias.and.resolveTo([
        {
          id: 't-1', statusSync: 'PENDENTE', idEstoqueOrigem: 'est-1',
          itens: [{ idMaterial: 'm-2', quantidade: 3, condicao: 'SUCATA' }]
        } as any,
        { id: 't-2', statusSync: 'PENDENTE', idEstoqueOrigem: 'est-other', itens: [] } as any
      ]);

      await service.reaplicarDeducoesPendentes('est-1');

      // Execução LOCAL (mat-1)
      expect(dbSpy.deduzirSaldoLocal).toHaveBeenCalledWith('est-1', 'm-1', 5, 'NOVO', undefined);
      // Transferência PENDENTE (mat-2)
      expect(dbSpy.deduzirSaldoLocal).toHaveBeenCalledWith('est-1', 'm-2', 3, 'SUCATA', undefined);
      
      // Chamado apenas 2 vezes
      expect(dbSpy.deduzirSaldoLocal).toHaveBeenCalledTimes(2);
    });
  });
});
