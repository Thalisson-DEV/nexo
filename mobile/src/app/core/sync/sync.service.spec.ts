import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import {
  SyncService,
  INSPECAO_ANEXO_ENDPOINT,
  INSPECAO_LANCAR_ENDPOINT,
} from './sync.service';
import { TRAVA_FILA } from './sync.service';
import { AuthService } from '../auth/auth.service';
import { TokenService } from '../auth/token.service';
import { DbService } from '../db/db.service';
import { OutboxService } from './outbox.service';
import { OutboxItem } from '../db/models';
import { TurnoService } from '../turno/turno.service';
import { ExecucoesService } from '../campo/execucoes.service';
import { environment } from '../../../environments/environment';
import { BaseOperacionalService } from '../sst/base-operacional.service';

describe('SyncService', () => {
  let service: SyncService;
  let httpMock: HttpTestingController;

  let mockAuthService: {
    isAdministrativo: any,
    isCampo: any,
    podeAcessarInspecoes: any,
    podeAcessarLogistica: any,
    currentUser: any,
    requireReauthentication: jasmine.Spy,
    tokenAtualizado: jasmine.Spy
  };
  let mockDbService: any;
  let mockOutboxService: any;
  let mockTurnoService: any;
  let mockExecucoesService: any;
  let mockBaseOperacionalService: any;

  /** Etapas de catálogo espionadas para isolar o teste da camada HTTP. */
  const ETAPAS_CATALOGO = [
    'syncEquipeDoUsuario', 'syncMembrosEquipe', 'syncTodasEquipes', 'syncTodosColaboradores',
    'syncMunicipios', 'syncVeiculos', 'syncProcessos', 'syncFormularios', 'syncTiposInspecao', 'syncAcoes', 'syncTiposServico',
    'syncMateriais', 'syncEstoques', 'syncSaldos', 'syncSeriais', 'pullParcialData'
  ];

  function stubEtapas(): void {
    ETAPAS_CATALOGO.forEach(nome => spyOn<any>(service, nome).and.returnValue(Promise.resolve()));
  }

  beforeEach(() => {
    mockAuthService = {
      isAdministrativo: signal(false),
      isCampo: signal(true),
      podeAcessarInspecoes: signal(false),
      podeAcessarLogistica: signal(true),
      currentUser: signal({ id: '123', equipeId: '456' }),
      requireReauthentication: jasmine.createSpy('requireReauthentication'),
      tokenAtualizado: jasmine.createSpy('tokenAtualizado').and.resolveTo('token-valido')
    };

    mockDbService = jasmine.createSpyObj('DbService', [
      'getConfig', 'setConfig', 'saveEquipe', 'replaceEquipes', 'replaceColaboradores',
      'replaceMunicipios', 'replaceVeiculos', 'replaceProcessos', 'replaceFormularios',
      'replaceTiposInspecao',
      'replaceAcoes', 'replaceTiposServico', 'replaceMateriais', 'replaceEstoques', 'bulkSaveMateriais',
      'replaceSaldos', 'replaceSaldosSeriais', 'replaceSolicitacoesSincronizadas',
      'replaceTransferenciasSincronizadas', 'replaceExecucoesSincronizadas',
      'replaceInspecoesSincronizadas', 'getInspecao', 'deleteInspecao', 'saveInspecao',
      'getSolicitacaoById', 'deleteSolicitacao', 'saveSolicitacao'
    ]);
    Object.values(mockDbService).forEach((spy: any) => spy.and?.returnValue(Promise.resolve()));
    mockDbService.getConfig.and.returnValue(Promise.resolve(null));

    mockOutboxService = jasmine.createSpyObj('OutboxService', [
      'getPendingItems', 'getErrorItems', 'discard', 'updateStatus', 'incrementAttempt',
      'updateEndpoint', 'updatePayload', 'enqueue', 'adiarNovaTentativa', 'proximoReenvioAgendado',
      'reivindicarParaEnvio'
    ]);
    // Por padrão a reivindicação devolve o item como a última leitura da fila o listou.
    mockOutboxService.reivindicarParaEnvio.and.callFake(async (id: string) => {
      const listados = await mockOutboxService.getPendingItems.calls.mostRecent()?.returnValue;
      return (listados as OutboxItem[] | undefined)?.find(i => i.id === id);
    });
    mockOutboxService.adiarNovaTentativa.and.returnValue(Promise.resolve(0));
    mockOutboxService.proximoReenvioAgendado.and.returnValue(Promise.resolve(null));
    mockOutboxService.getPendingItems.and.returnValue(Promise.resolve([]));
    mockOutboxService.getErrorItems.and.returnValue(Promise.resolve([]));
    mockOutboxService.discard.and.returnValue(Promise.resolve());
    mockOutboxService.updateStatus.and.returnValue(Promise.resolve());
    mockOutboxService.incrementAttempt.and.returnValue(Promise.resolve(1));

    mockTurnoService = jasmine.createSpyObj('TurnoService', ['syncTurnoAtivoComServidor', 'sincronizarComposicao']);
    mockTurnoService.sincronizarComposicao.and.returnValue(Promise.resolve({}));
    mockTurnoService.syncTurnoAtivoComServidor.and.returnValue(Promise.resolve());

    mockExecucoesService = jasmine.createSpyObj('ExecucoesService', ['reaplicarDeducoesPendentes']);
    mockExecucoesService.reaplicarDeducoesPendentes.and.returnValue(Promise.resolve());
    mockBaseOperacionalService = jasmine.createSpyObj('BaseOperacionalService', ['sincronizarAtivas']);
    mockBaseOperacionalService.sincronizarAtivas.and.returnValue(Promise.resolve([]));

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        // Relógio falso (fakeAsync) não avança o callback do Web Lock: trava de passagem.
        { provide: TRAVA_FILA, useValue: (fn: () => Promise<unknown>) => fn() },
        SyncService,
        { provide: AuthService, useValue: mockAuthService },
        { provide: DbService, useValue: mockDbService },
        { provide: OutboxService, useValue: mockOutboxService },
        {
          provide: TokenService,
          useValue: {
            getDecodedPayload: () => ({ sub: 'user-1' }),
            equipeId: signal('456'),
            estoqueId: signal('est-1'),
            usuarioId: signal('user-1'),
            token: signal('token-valido'),
            isTokenExpired: () => false,
            reautenticacaoNecessaria: signal(false)
          }
        },
        { provide: TurnoService, useValue: mockTurnoService },
        { provide: ExecucoesService, useValue: mockExecucoesService },
        { provide: BaseOperacionalService, useValue: mockBaseOperacionalService }
      ]
    });

    service = TestBed.inject(SyncService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('syncTotal() — resiliência a permissões', () => {
    it('conclui o sync mesmo quando um catálogo responde 403', async () => {
      stubEtapas();
      (service as any).syncVeiculos.and.returnValue(Promise.reject({ status: 403 }));

      const result = await service.syncTotal();

      // O sync não aborta: as etapas seguintes continuam sendo executadas.
      expect((service as any).syncProcessos).toHaveBeenCalled();
      expect((service as any).syncMateriais).toHaveBeenCalled();
      expect(mockDbService.setConfig).toHaveBeenCalledWith('lastSyncAt', jasmine.any(String));

      expect(result.falhas.length).toBe(1);
      expect(result.falhas[0].etapa).toBe('Frota de veículos');
      expect(result.falhas[0].semPermissao).toBeTrue();
      expect(service.falhasUltimoSync()).toEqual(result.falhas);
      expect(service.isSyncing()).toBeFalse();
    });

    it('registra falha sem marcar semPermissao quando o erro não é 403', async () => {
      stubEtapas();
      (service as any).syncAcoes.and.returnValue(Promise.reject({ status: 500, detail: 'Boom' }));

      const result = await service.syncTotal();

      expect(result.falhas.length).toBe(1);
      expect(result.falhas[0]).toEqual({
        etapa: 'Ações comerciais',
        motivo: 'Boom',
        semPermissao: false,
        status: 500
      });
    });

    it('não sincroniza formulários quando o usuário não é administrativo', async () => {
      stubEtapas();

      await service.syncTotal();

      expect((service as any).syncFormularios).not.toHaveBeenCalled();
      expect((service as any).syncTiposInspecao).not.toHaveBeenCalled();
      expect((service as any).syncEquipeDoUsuario).toHaveBeenCalledWith('456');
    });

    it('sincroniza formulários e catálogos globais quando o usuário é administrativo', async () => {
      mockAuthService.isAdministrativo.set(true);
      mockAuthService.isCampo.set(false);
      mockAuthService.podeAcessarInspecoes.set(true);
      mockAuthService.podeAcessarLogistica.set(false);
      stubEtapas();

      await service.syncTotal();

      expect((service as any).syncFormularios).toHaveBeenCalled();
      expect((service as any).syncTiposInspecao).toHaveBeenCalled();
      expect((service as any).syncTodasEquipes).toHaveBeenCalled();
      expect((service as any).syncEquipeDoUsuario).not.toHaveBeenCalled();
    });

    it('não baixa catálogos de material para o perfil administrativo', async () => {
      mockAuthService.isAdministrativo.set(true);
      mockAuthService.isCampo.set(false);
      mockAuthService.podeAcessarInspecoes.set(true);
      mockAuthService.podeAcessarLogistica.set(false);
      stubEtapas();

      const result = await service.syncTotal();

      // Endpoints de logística respondem 403 para o administrativo: nem são chamados.
      expect((service as any).syncAcoes).not.toHaveBeenCalled();
      expect((service as any).syncMateriais).not.toHaveBeenCalled();
      expect((service as any).syncEstoques).not.toHaveBeenCalled();
      expect((service as any).syncSaldos).not.toHaveBeenCalled();
      expect((service as any).syncSeriais).not.toHaveBeenCalled();
      expect(mockExecucoesService.reaplicarDeducoesPendentes).not.toHaveBeenCalled();

      // Sem etapas negadas, o login não exibe o alerta de dados não carregados.
      expect(result.falhas).toEqual([]);
    });

    it('não repete o catálogo de materiais no pull final', async () => {
      stubEtapas();
      (service as any).pullParcialData.and.callThrough();
      ['syncSolicitacoes', 'syncTransferencias', 'syncExecucoes']
        .forEach(nome => spyOn<any>(service, nome).and.returnValue(Promise.resolve()));

      await service.syncTotal();

      expect((service as any).syncMateriais).toHaveBeenCalledTimes(1);
    });

    it('reaplica as deduções locais somente após saldos e seriais', async () => {
      stubEtapas();

      await service.syncTotal();

      expect(mockExecucoesService.reaplicarDeducoesPendentes).toHaveBeenCalledWith('est-1');
      expect((service as any).syncSaldos).toHaveBeenCalledBefore(
        mockExecucoesService.reaplicarDeducoesPendentes
      );
      expect((service as any).syncSeriais).toHaveBeenCalledBefore(
        mockExecucoesService.reaplicarDeducoesPendentes
      );
    });
  });

  describe('composição da equipe', () => {
    it('sincroniza a composição e meta da equipe no sync total do usuário de campo', async () => {
      stubEtapas();

      await service.syncTotal();

      expect(mockTurnoService.sincronizarComposicao).toHaveBeenCalled();
    });
  });

  describe('escopo do usuário de campo', () => {
    it('não baixa inspeções de segurança para usuário de campo', async () => {
      spyOn<any>(service, 'syncMateriais').and.returnValue(Promise.resolve());
      spyOn<any>(service, 'syncSolicitacoes').and.returnValue(Promise.resolve());
      spyOn<any>(service, 'syncTransferencias').and.returnValue(Promise.resolve());
      spyOn<any>(service, 'syncSaldos').and.returnValue(Promise.resolve());
      spyOn<any>(service, 'syncSeriais').and.returnValue(Promise.resolve());
      spyOn<any>(service, 'syncExecucoes').and.returnValue(Promise.resolve());
      const inspecoesSpy = spyOn<any>(service, 'syncInspecoes').and.returnValue(Promise.resolve());

      await (service as any).pullParcialData([]);

      expect(inspecoesSpy).not.toHaveBeenCalled();
      expect((service as any).syncExecucoes).toHaveBeenCalled();
    });

    it('baixa inspeções de segurança para usuário administrativo', async () => {
      mockAuthService.isAdministrativo.set(true);
      mockAuthService.isCampo.set(false);
      mockAuthService.podeAcessarInspecoes.set(true);
      mockAuthService.podeAcessarLogistica.set(false);

      spyOn<any>(service, 'syncMateriais').and.returnValue(Promise.resolve());
      spyOn<any>(service, 'syncSolicitacoes').and.returnValue(Promise.resolve());
      spyOn<any>(service, 'syncTransferencias').and.returnValue(Promise.resolve());
      spyOn<any>(service, 'syncSaldos').and.returnValue(Promise.resolve());
      spyOn<any>(service, 'syncSeriais').and.returnValue(Promise.resolve());
      const inspecoesSpy = spyOn<any>(service, 'syncInspecoes').and.returnValue(Promise.resolve());

      await (service as any).pullParcialData([]);

      expect(inspecoesSpy).toHaveBeenCalled();
    });

    it('não puxa listas de material no pull do perfil administrativo', async () => {
      mockAuthService.isAdministrativo.set(true);
      mockAuthService.isCampo.set(false);
      mockAuthService.podeAcessarInspecoes.set(true);
      mockAuthService.podeAcessarLogistica.set(false);

      spyOn<any>(service, 'syncMateriais').and.returnValue(Promise.resolve());
      spyOn<any>(service, 'syncSolicitacoes').and.returnValue(Promise.resolve());
      spyOn<any>(service, 'syncTransferencias').and.returnValue(Promise.resolve());
      spyOn<any>(service, 'syncSaldos').and.returnValue(Promise.resolve());
      spyOn<any>(service, 'syncSeriais').and.returnValue(Promise.resolve());
      spyOn<any>(service, 'syncInspecoes').and.returnValue(Promise.resolve());

      const falhas: any[] = [];
      await (service as any).pullParcialData(falhas);

      expect((service as any).syncMateriais).not.toHaveBeenCalled();
      expect((service as any).syncSolicitacoes).not.toHaveBeenCalled();
      expect((service as any).syncTransferencias).not.toHaveBeenCalled();
      expect((service as any).syncSaldos).not.toHaveBeenCalled();
      expect((service as any).syncSeriais).not.toHaveBeenCalled();
      expect(mockExecucoesService.reaplicarDeducoesPendentes).not.toHaveBeenCalled();
      expect(falhas).toEqual([]);
    });
  });

  describe('syncSolicitacoes()', () => {
    it('ignora item cujo detalhe falha, mas mantém os demais', fakeAsync(() => {
      (service as any).syncSolicitacoes();

      httpMock.expectOne(r => r.url === `${environment.apiBaseUrl}/v1/solicitacoes-material`)
        .flush({ content: [{ id: 's-1' }, { id: 's-2' }], page: { totalPages: 1 } });
      tick();

      httpMock.expectOne(`${environment.apiBaseUrl}/v1/solicitacoes-material/s-1`)
        .flush(null, { status: 404, statusText: 'Not Found' });
      tick();

      httpMock.expectOne(`${environment.apiBaseUrl}/v1/solicitacoes-material/s-2`)
        .flush({ id: 's-2', itens: [] });
      tick();

      expect(mockDbService.replaceSolicitacoesSincronizadas).toHaveBeenCalledWith([
        jasmine.objectContaining({ id: 's-2' })
      ]);
    }));

    it('falha em vez de apagar o histórico quando nenhum detalhe é acessível', fakeAsync(() => {
      let erro: any = null;
      (service as any).syncSolicitacoes().catch((e: any) => erro = e);

      httpMock.expectOne(r => r.url === `${environment.apiBaseUrl}/v1/solicitacoes-material`)
        .flush({ content: [{ id: 's-1' }], page: { totalPages: 1 } });
      tick();

      httpMock.expectOne(`${environment.apiBaseUrl}/v1/solicitacoes-material/s-1`)
        .flush(null, { status: 403, statusText: 'Forbidden' });
      tick();

      expect(erro).toBeTruthy();
      expect(mockDbService.replaceSolicitacoesSincronizadas).not.toHaveBeenCalled();
    }));
  });

  describe('syncMunicipios() — preservação do cache local', () => {
    it('não apaga o catálogo local quando o endpoint responde 403', fakeAsync(() => {
      let erro: any = null;
      (service as any).syncMunicipios().catch((e: any) => erro = e);

      const req = httpMock.expectOne(r => r.url === `${environment.apiBaseUrl}/v1/municipios`);
      req.flush({ title: 'Acesso negado' }, { status: 403, statusText: 'Forbidden' });
      tick();

      expect(erro).toBeTruthy();
      // Crítico: um replace com lista vazia limparia os municípios já cacheados.
      expect(mockDbService.replaceMunicipios).not.toHaveBeenCalled();
    }));
  });

  describe('syncTiposServico()', () => {
    it('guarda no aparelho os tipos de serviço ativos', fakeAsync(() => {
      (service as any).syncTiposServico();

      httpMock.expectOne(`${environment.apiBaseUrl}/v1/tipos-servico/opcoes`).flush([
        { id: 'ts-cs', codigo: 'CS', descricao: 'CORTE', ativo: true, dataCriacao: '2026-10-11T00:00:00Z' }
      ]);
      tick();

      expect(mockDbService.replaceTiposServico).toHaveBeenCalledWith([
        { id: 'ts-cs', codigo: 'CS', descricao: 'CORTE', ativo: true }
      ]);
    }));

    it('não apaga os tipos já guardados quando o endpoint falha', fakeAsync(() => {
      let erro: any = null;
      (service as any).syncTiposServico().catch((e: any) => erro = e);

      httpMock.expectOne(`${environment.apiBaseUrl}/v1/tipos-servico/opcoes`)
        .flush({ title: 'Erro' }, { status: 500, statusText: 'Server Error' });
      tick();

      expect(erro).toBeTruthy();
      expect(mockDbService.replaceTiposServico).not.toHaveBeenCalled();
    }));
  });

  describe('syncOutbox()', () => {
    it('mantém operação na fila quando o servidor responde 401', fakeAsync(() => {
      mockOutboxService.getPendingItems.and.returnValue(Promise.resolve([{
        id: 'out-auth', endpoint: '/v1/teste', method: 'POST', payload: {},
        status: 'PENDENTE', tentativas: 0, criadoEm: new Date()
      }]));

      let erro: any;
      service.syncOutbox({ reconciliar: false }).catch(e => erro = e);
      tick();
      httpMock.expectOne(`${environment.apiBaseUrl}/v1/teste`)
        .flush(null, { status: 401, statusText: 'Unauthorized' });
      tick();

      expect(erro.status).toBe(401);
      expect(mockOutboxService.updateStatus).toHaveBeenCalledWith(
        'out-auth', 'PENDENTE', jasmine.stringMatching(/mesma conta/)
      );
      expect(mockOutboxService.discard).not.toHaveBeenCalledWith('out-auth');
    }));

    it('não envia a versão que foi corrigida depois de a fila ser lida', fakeAsync(() => {
      mockOutboxService.getPendingItems.and.returnValue(Promise.resolve([{
        id: 'versao-antiga', endpoint: '/_execucoes-comerciais/lancar', method: 'POST',
        payload: { tempId: 'v1', body: { referenciaNota: '4605144730' } },
        status: 'PENDENTE', tentativas: 0, criadoEm: new Date()
      }]));
      // A correção retirou a versão da fila entre a leitura e o envio.
      mockOutboxService.reivindicarParaEnvio.and.returnValue(Promise.resolve(undefined));

      service.syncOutbox({ reconciliar: false });
      tick();

      httpMock.expectNone(`${environment.apiBaseUrl}/v1/execucoes-comerciais`);
      expect(mockOutboxService.reivindicarParaEnvio).toHaveBeenCalledWith('versao-antiga');
    }));

    it('envia a solicitação comercial sem tempId e preserva o status pendente retornado pela criação', fakeAsync(() => {
      const local = {
        id: 'temp-1', idEstoqueOrigem: 'origem', idEstoqueDestino: 'destino',
        idRequisitante: 'user-1', status: 'PENDENTE', itens: []
      };
      mockDbService.getSolicitacaoById.and.returnValue(Promise.resolve(local));
      mockOutboxService.getPendingItems.and.returnValue(Promise.resolve([{
        id: 'out-sol', endpoint: '/v1/solicitacoes-material', method: 'POST',
        payload: { tempId: 'temp-1', idEstoqueOrigem: 'origem', idEquipe: 'equipe', dataPrevista: '2026-09-27', itens: [] },
        status: 'PENDENTE', tentativas: 0, criadoEm: new Date()
      }]));

      service.syncOutbox({ reconciliar: false });
      tick();

      const req = httpMock.expectOne(`${environment.apiBaseUrl}/v1/solicitacoes-material`);
      expect(req.request.body).toEqual({ idEstoqueOrigem: 'origem', idEquipe: 'equipe', dataPrevista: '2026-09-27', itens: [] });
      req.flush('server-1');
      tick();

      expect(mockDbService.saveSolicitacao).toHaveBeenCalledWith(jasmine.objectContaining({
        id: 'server-1', status: 'PENDENTE'
      }));
    }));

    it('coalesces concurrent executions so the same item is sent only once', fakeAsync(() => {
      spyOn<any>(service, 'pullParcialData').and.returnValue(Promise.resolve());

      mockOutboxService.getPendingItems.and.returnValue(Promise.resolve([
        {
          id: 'out-concurrent',
          endpoint: '/v1/teste-concorrente',
          method: 'POST',
          payload: { foo: 'bar' },
          status: 'PENDENTE',
          tentativas: 0,
          criadoEm: new Date()
        }
      ]));

      let firstResult: any;
      let secondResult: any;
      service.syncOutbox({ reconciliar: false }).then(result => firstResult = result);
      service.syncOutbox({ reconciliar: false }).then(result => secondResult = result);
      tick();

      const requests = httpMock.match(`${environment.apiBaseUrl}/v1/teste-concorrente`);
      expect(requests.length).toBe(1);
      requests[0].flush({ success: true });
      tick();

      expect(firstResult.sincronizados).toBe(1);
      expect(secondResult).toEqual(firstResult);
      expect(mockOutboxService.discard).toHaveBeenCalledOnceWith('out-concurrent');
    }));

    it('processa no mesmo ciclo itens enfileirados durante a sincronização', fakeAsync(() => {
      const itemInspecao = {
        id: 'out-inspecao',
        endpoint: '/v1/teste-inspecao',
        method: 'POST',
        payload: { tipo: 'inspecao' },
        status: 'PENDENTE',
        tentativas: 0,
        criadoEm: new Date()
      };
      const itemAnexo = {
        id: 'out-anexo',
        endpoint: '/v1/teste-anexo',
        method: 'POST',
        payload: { tipo: 'anexo' },
        status: 'PENDENTE',
        tentativas: 0,
        criadoEm: new Date()
      };
      let leitura = 0;
      mockOutboxService.getPendingItems.and.callFake(() => {
        leitura++;
        return Promise.resolve(leitura === 1 ? [itemInspecao] : [itemAnexo]);
      });

      let result: any;
      service.syncOutbox({ reconciliar: false }).then(value => result = value);
      tick();

      const inspecao = httpMock.expectOne(`${environment.apiBaseUrl}/v1/teste-inspecao`);
      inspecao.flush({ success: true });
      tick();

      const anexo = httpMock.expectOne(`${environment.apiBaseUrl}/v1/teste-anexo`);
      anexo.flush({ success: true });
      tick();

      expect(result).toEqual({ sincronizados: 2, erros: 0, falhas: [] });
      expect(mockOutboxService.discard).toHaveBeenCalledWith('out-inspecao');
      expect(mockOutboxService.discard).toHaveBeenCalledWith('out-anexo');
    }));

    it('processa um item pendente com sucesso', fakeAsync(() => {
      spyOn<any>(service, 'pullParcialData').and.returnValue(Promise.resolve());

      mockOutboxService.getPendingItems.and.returnValue(Promise.resolve([
        {
          id: 'out-1',
          endpoint: '/v1/teste',
          method: 'POST',
          payload: { foo: 'bar' },
          status: 'PENDENTE',
          tentativas: 0,
          criadoEm: new Date()
        }
      ]));

      let result: any;
      service.syncOutbox().then(r => result = r);
      tick();

      const req = httpMock.expectOne(`${environment.apiBaseUrl}/v1/teste`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ foo: 'bar' });
      req.flush({ success: true });
      tick();

      expect(result.sincronizados).toBe(1);
      expect(result.erros).toBe(0);
      expect(mockOutboxService.updateStatus).toHaveBeenCalledWith('out-1', 'SINCRONIZADO');
      expect(mockOutboxService.discard).toHaveBeenCalledWith('out-1');
      expect((service as any).pullParcialData).toHaveBeenCalled();
    }));

    it('não reconcilia quando reconciliar=false (evita pull duplicado)', fakeAsync(() => {
      spyOn<any>(service, 'pullParcialData').and.returnValue(Promise.resolve());

      let result: any;
      service.syncOutbox({ reconciliar: false }).then(r => result = r);
      tick();

      expect(result).toEqual({ sincronizados: 0, erros: 0, falhas: [] });
      expect((service as any).pullParcialData).not.toHaveBeenCalled();
    }));

    it('preserva o detalhe do backend já normalizado pelo errorInterceptor', fakeAsync(() => {
      spyOn<any>(service, 'pullParcialData').and.returnValue(Promise.resolve());

      mockOutboxService.getPendingItems.and.returnValue(Promise.resolve([
        {
          id: 'out-err',
          endpoint: '/v1/erro',
          method: 'POST',
          payload: {},
          status: 'PENDENTE',
          tentativas: 0,
          criadoEm: new Date()
        }
      ]));

      let result: any;
      service.syncOutbox().then(r => result = r);
      tick();

      const req = httpMock.expectOne(`${environment.apiBaseUrl}/v1/erro`);
      req.flush({ detail: 'Item inválido' }, { status: 400, statusText: 'Bad Request' });
      tick();

      expect(result.erros).toBe(1);
      expect(mockOutboxService.updateStatus).toHaveBeenCalledWith('out-err', 'ERRO', 'Item inválido');
      expect(mockOutboxService.incrementAttempt).not.toHaveBeenCalled();
    }));

    it('retém o item com mensagem acionável e sem retry quando recebe 403', fakeAsync(() => {
      spyOn<any>(service, 'pullParcialData').and.returnValue(Promise.resolve());

      mockOutboxService.getPendingItems.and.returnValue(Promise.resolve([
        {
          id: 'out-403',
          endpoint: '/v1/proibido',
          method: 'POST',
          payload: {},
          status: 'PENDENTE',
          tentativas: 0,
          criadoEm: new Date()
        }
      ]));

      service.syncOutbox();
      tick();

      const req = httpMock.expectOne(`${environment.apiBaseUrl}/v1/proibido`);
      req.flush(null, { status: 403, statusText: 'Forbidden' });
      tick();

      expect(mockOutboxService.updateStatus).toHaveBeenCalledWith(
        'out-403',
        'ERRO',
        'Seu perfil de acesso não permite enviar esta operação. Fale com o administrador do sistema.'
      );
      expect(mockOutboxService.incrementAttempt).not.toHaveBeenCalled();
    }));
  });

  describe('syncFormularios()', () => {
    it('preserva o cache anterior quando qualquer detalhe de formulário falha', fakeAsync(() => {
      let erro: unknown;
      (service as any).syncFormularios().catch((e: unknown) => erro = e);
      tick();

      const lista = httpMock.expectOne(request =>
        request.url === `${environment.apiBaseUrl}/v1/sst/formularios-inspecao?ativo=true`
      );
      lista.flush({
        content: [{ id: 'form-1' }, { id: 'form-2' }],
        page: { totalPages: 1 }
      });
      tick();

      const primeiro = httpMock.expectOne(
        `${environment.apiBaseUrl}/v1/sst/formularios-inspecao/form-1`
      );
      primeiro.flush({ id: 'form-1', titulo: 'Formulário 1', grupos: [] });
      tick();

      const segundo = httpMock.expectOne(
        `${environment.apiBaseUrl}/v1/sst/formularios-inspecao/form-2`
      );
      segundo.flush(null, { status: 503, statusText: 'Service Unavailable' });
      tick();

      expect(erro).toBeTruthy();
      expect(mockDbService.replaceFormularios).not.toHaveBeenCalled();
    }));
  });

  describe('processarInspecaoLancamento', () => {
    /**
     * As evidências sobem antes do lançamento, então o `POST /inspecoes` só é
     * emitido depois de alguns `await`. Drena a fila de microtarefas antes de
     * checar a próxima requisição.
     */
    const drenarPendencias = () => new Promise<void>(resolve => setTimeout(resolve, 0));

    const novaEvidencia = (idLocal: string, idRemoto: string | null = null) => ({
      idLocal,
      arquivo: new File(['foto'], `${idLocal}.jpg`, { type: 'image/jpeg' }),
      nomeArquivo: `${idLocal}.jpg`,
      contentType: 'image/jpeg',
      idRemoto,
      estado: idRemoto ? 'ENVIADA' : 'LOCAL',
      tentativas: idRemoto ? 1 : 0,
    });

    it('envia somente o contrato aceito e grava a resposta canônica', async () => {
      const mockItem = {
        id: 'local-id',
        endpoint: INSPECAO_LANCAR_ENDPOINT,
        method: 'POST',
        payload: {
          request: {
            idFormulario: 'form-xyz',
            idProcesso: 'processo-1',
            idBaseOperacional: 'base-1',
            dataRealizacao: '2026-07-31T08:30:00-03:00',
            colaboradoresInspecionados: ['c1'],
            respostas: [{ idPergunta: 'p1', opcoesSelecionadas: ['r1'] }],
            desvios: [{
              idPergunta: 'p1', idOpcaoResposta: 'r1', descricao: 'Teste',
              colaboradores: ['c1'], gravidade: 'CRITICA', dataResolucao: 'indevida'
            }]
          },
          anexosPendentes: [],
          evidencias: [novaEvidencia('ev-1', 'remoto-1')]
        },
        status: 'PENDENTE'
      };

      mockDbService.getInspecao.and.returnValue(
        Promise.resolve({ id: 'local-id', status: 'EM_ANDAMENTO' })
      );

      const processPromise = (service as any).processarInspecaoLancamento(mockItem);
      await drenarPendencias();

      const req = httpMock.expectOne(`${environment.apiBaseUrl}/v1/sst/inspecoes`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body.idInspetor).toBeUndefined();
      expect(req.request.body.desvios[0].gravidade).toBeUndefined();
      expect(req.request.body.desvios[0].dataResolucao).toBeUndefined();
      expect(req.request.body.evidencias).toEqual(['remoto-1']);

      req.flush({
        id: 'real-server-id', idInspetor: 'inspetor-1', status: 'CONCLUIDA',
        dataRealizacao: '2026-07-31T08:30:01-03:00', observacoes: null,
        latitude: null, longitude: null, respostas: [], desvios: [],
        evidencias: [{ id: 'remoto-1', nomeArquivo: 'ev-1.jpg' }]
      });

      await processPromise;

      expect(mockOutboxService.updatePayload).toHaveBeenCalledWith(
        'local-id',
        jasmine.objectContaining({
          respostaServidor: jasmine.objectContaining({ id: 'real-server-id' })
        })
      );
      expect(mockDbService.deleteInspecao).toHaveBeenCalledWith('local-id');
      expect(mockDbService.saveInspecao).toHaveBeenCalledWith(jasmine.objectContaining({
        id: 'real-server-id',
        status: 'CONCLUIDA',
        statusSync: 'SINCRONIZADO',
        erroDetalhes: undefined,
        // Blobs liberados só depois do 201 do lançamento.
        evidencias: [],
        evidenciasRemotas: [{ id: 'remoto-1', nomeArquivo: 'ev-1.jpg' }]
      }));
    });

    it('sobe as evidências antes do lançamento e persiste cada idRemoto no 201', async () => {
      const mockItem = {
        id: 'local-id',
        endpoint: INSPECAO_LANCAR_ENDPOINT,
        method: 'POST',
        payload: {
          request: {
            idFormulario: 'form-xyz', idProcesso: 'processo-1',
            dataRealizacao: '2026-07-31T08:30:00-03:00',
            colaboradoresInspecionados: ['c1'], respostas: [], desvios: []
          },
          anexosPendentes: [],
          evidencias: [novaEvidencia('ev-1'), novaEvidencia('ev-2')]
        },
        status: 'PENDENTE'
      };
      mockDbService.getInspecao.and.returnValue(Promise.resolve(undefined));

      // O payload é gravado por referência: só uma cópia no momento da chamada
      // mostra o que estava persistido em cada etapa.
      const checkpoints: { idRemoto?: string | null; estado: string }[][] = [];
      mockOutboxService.updatePayload.and.callFake((_id: string, payload: any) => {
        checkpoints.push((payload.evidencias ?? []).map((evidencia: any) => ({
          idRemoto: evidencia.idRemoto,
          estado: evidencia.estado,
        })));
        return Promise.resolve();
      });

      const promise = (service as any).processarInspecaoLancamento(mockItem);
      await drenarPendencias();

      const url = `${environment.apiBaseUrl}/v1/sst/inspecoes/anexos`;
      const primeiro = httpMock.expectOne(url);
      expect(primeiro.request.body instanceof FormData).toBeTrue();
      expect((primeiro.request.body.get('arquivo') as File).name).toBe('ev-1.jpg');
      // Uma foto por requisição: a segunda só sai depois do 201 da primeira.
      httpMock.expectNone(url);
      primeiro.flush({ id: 'remoto-1' });
      await drenarPendencias();

      const segundo = httpMock.expectOne(url);
      segundo.flush({ id: 'remoto-2' });
      await drenarPendencias();

      const lancamento = httpMock.expectOne(`${environment.apiBaseUrl}/v1/sst/inspecoes`);
      expect(lancamento.request.body.evidencias).toEqual(['remoto-1', 'remoto-2']);
      lancamento.flush({
        id: 'inspecao-1', idInspetor: 'inspetor-1', status: 'CONCLUIDA',
        dataRealizacao: '2026-07-31T08:30:01-03:00', respostas: [], desvios: [],
        evidencias: [{ id: 'remoto-1' }, { id: 'remoto-2' }]
      });
      await promise;

      // O idRemoto da primeira foto já estava persistido enquanto a segunda
      // ainda nem tinha subido — é isso que evita reenviar foto já enviada.
      expect(checkpoints.length).toBeGreaterThanOrEqual(3);
      expect(checkpoints[0][0]).toEqual({ idRemoto: 'remoto-1', estado: 'ENVIADA' });
      expect(checkpoints[0][1].idRemoto).toBeFalsy();
      expect(checkpoints[1][1]).toEqual({ idRemoto: 'remoto-2', estado: 'ENVIADA' });
    });

    it('não reenvia a foto já enviada quando só o lançamento falhou', async () => {
      const mockItem = {
        id: 'local-id',
        endpoint: INSPECAO_LANCAR_ENDPOINT,
        method: 'POST',
        payload: {
          request: {
            idFormulario: 'form-xyz', idProcesso: 'processo-1',
            dataRealizacao: '2026-07-31T08:30:00-03:00',
            colaboradoresInspecionados: ['c1'], respostas: [], desvios: []
          },
          anexosPendentes: [],
          evidencias: [novaEvidencia('ev-1', 'remoto-1')]
        },
        status: 'PENDENTE'
      };
      mockDbService.getInspecao.and.returnValue(Promise.resolve(undefined));

      const promise = (service as any).processarInspecaoLancamento(mockItem);
      await drenarPendencias();
      httpMock.expectNone(`${environment.apiBaseUrl}/v1/sst/inspecoes/anexos`);

      const lancamento = httpMock.expectOne(`${environment.apiBaseUrl}/v1/sst/inspecoes`);
      lancamento.flush({
        id: 'inspecao-1', idInspetor: 'inspetor-1', status: 'CONCLUIDA',
        dataRealizacao: '2026-07-31T08:30:01-03:00', respostas: [], desvios: [],
        evidencias: []
      });

      await promise;
      expect(mockOutboxService.discard).toHaveBeenCalledWith('local-id');
    });

    it('reenvia o Blob e refaz o lançamento quando a evidência foi expurgada (404)', async () => {
      const mockItem = {
        id: 'local-id',
        endpoint: INSPECAO_LANCAR_ENDPOINT,
        method: 'POST',
        payload: {
          request: {
            idFormulario: 'form-xyz', idProcesso: 'processo-1',
            dataRealizacao: '2026-07-31T08:30:00-03:00',
            colaboradoresInspecionados: ['c1'], respostas: [], desvios: []
          },
          anexosPendentes: [],
          evidencias: [novaEvidencia('ev-1', 'expurgado-1')]
        },
        status: 'PENDENTE'
      };
      mockDbService.getInspecao.and.returnValue(Promise.resolve(undefined));

      const promise = (service as any).processarInspecaoLancamento(mockItem);
      await drenarPendencias();

      const primeiroLancamento = httpMock.expectOne(`${environment.apiBaseUrl}/v1/sst/inspecoes`);
      expect(primeiroLancamento.request.body.evidencias).toEqual(['expurgado-1']);
      primeiroLancamento.flush(
        { detail: 'Evidência não encontrada.' },
        { status: 404, statusText: 'Not Found' }
      );
      await drenarPendencias();

      const reenvio = httpMock.expectOne(`${environment.apiBaseUrl}/v1/sst/inspecoes/anexos`);
      reenvio.flush({ id: 'remoto-novo' });
      await drenarPendencias();

      const segundoLancamento = httpMock.expectOne(`${environment.apiBaseUrl}/v1/sst/inspecoes`);
      expect(segundoLancamento.request.body.evidencias).toEqual(['remoto-novo']);
      segundoLancamento.flush({
        id: 'inspecao-1', idInspetor: 'inspetor-1', status: 'CONCLUIDA',
        dataRealizacao: '2026-07-31T08:30:01-03:00', respostas: [], desvios: [],
        evidencias: [{ id: 'remoto-novo' }]
      });

      await promise;
      expect(mockOutboxService.discard).toHaveBeenCalledWith('local-id');
    });

    it('não marca resultado incerto quando a rede cai durante o upload da foto', async () => {
      const item = {
        id: 'out-upload',
        endpoint: INSPECAO_LANCAR_ENDPOINT,
        method: 'POST',
        payload: {
          request: {
            idFormulario: 'form-xyz', idProcesso: 'processo-1',
            dataRealizacao: '2026-07-31T08:30:00-03:00',
            colaboradoresInspecionados: ['c1'], respostas: [], desvios: []
          },
          anexosPendentes: [],
          evidencias: [novaEvidencia('ev-1')]
        },
        status: 'PENDENTE',
        tentativas: 0
      };
      mockOutboxService.getPendingItems.and.returnValues(
        Promise.resolve([item]),
        Promise.resolve([])
      );
      mockDbService.getInspecao.and.returnValue(Promise.resolve(undefined));

      const promise = service.syncOutbox({ reconciliar: false });
      await drenarPendencias();

      httpMock.expectOne(`${environment.apiBaseUrl}/v1/sst/inspecoes/anexos`)
        .error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });
      await promise;

      // A inspeção sequer chegou a ser criada: nada de "envio a confirmar".
      expect(mockOutboxService.updateStatus).not.toHaveBeenCalledWith(
        'out-upload', 'RESULTADO_INCERTO', jasmine.anything()
      );
      expect(mockOutboxService.updateStatus).toHaveBeenCalledWith(
        'out-upload', 'PENDENTE', jasmine.any(String)
      );
    });

    it('retém o item com mensagem acionável quando não há nenhuma evidência', async () => {
      const mockItem = {
        id: 'local-id',
        endpoint: INSPECAO_LANCAR_ENDPOINT,
        method: 'POST',
        payload: {
          request: {
            idFormulario: 'form-xyz', idProcesso: 'processo-1',
            dataRealizacao: '2026-07-31T08:30:00-03:00',
            colaboradoresInspecionados: ['c1'], respostas: [], desvios: []
          },
          anexosPendentes: [],
          evidencias: []
        },
        status: 'PENDENTE'
      };

      await expectAsync((service as any).processarInspecaoLancamento(mockItem))
        .toBeRejectedWith(jasmine.objectContaining({ status: 400 }));
      httpMock.expectNone(`${environment.apiBaseUrl}/v1/sst/inspecoes`);
    });

    it('retoma a reconciliação pelo checkpoint sem reenviar a inspeção', async () => {
      const respostaServidor = {
        id: 'real-server-id', idInspetor: 'inspetor-1', status: 'CONCLUIDA',
        dataRealizacao: '2026-07-31T08:30:01-03:00', observacoes: null,
        latitude: null, longitude: null, respostas: [], desvios: []
      };
      const mockItem = {
        id: 'local-id',
        endpoint: INSPECAO_LANCAR_ENDPOINT,
        method: 'POST',
        payload: {
          request: {
            idFormulario: 'form-xyz',
            idProcesso: 'processo-1',
            dataRealizacao: '2026-07-31T08:30:00-03:00',
            colaboradoresInspecionados: ['c1'],
            respostas: [],
            desvios: []
          },
          anexosPendentes: [],
          respostaServidor
        },
        status: 'PENDENTE'
      };
      mockDbService.getInspecao.and.returnValue(
        Promise.resolve({ id: 'local-id', status: 'CONCLUIDA' })
      );

      await (service as any).processarInspecaoLancamento(mockItem);

      httpMock.expectNone(`${environment.apiBaseUrl}/v1/sst/inspecoes`);
      expect(mockDbService.saveInspecao).toHaveBeenCalledWith(jasmine.objectContaining({
        id: 'real-server-id',
        statusSync: 'SINCRONIZADO'
      }));
      expect(mockOutboxService.discard).toHaveBeenCalledWith('local-id');
    });

    it('mapeia o anexo para o desvio criado pelo servidor', async () => {
      const arquivo = new File(['foto'], 'evidencia.jpg', { type: 'image/jpeg' });
      const anexo = {
        idLocal: 'anexo-1',
        chaveDesvioLocal: 'p1:r1',
        contexto: 'DESVIO',
        arquivo,
        nomeArquivo: 'evidencia.jpg',
        contentType: 'image/jpeg'
      };
      const mockItem = {
        id: 'local-id',
        endpoint: INSPECAO_LANCAR_ENDPOINT,
        method: 'POST',
        payload: {
          request: {
            idFormulario: 'form-xyz', idProcesso: 'processo-1',
            dataRealizacao: '2026-07-31T08:30:00-03:00',
            colaboradoresInspecionados: ['c1'], respostas: [], desvios: []
          },
          anexosPendentes: [anexo],
          evidencias: [novaEvidencia('ev-1', 'remoto-1')]
        },
        status: 'PENDENTE'
      };
      mockDbService.getInspecao.and.returnValue(Promise.resolve(undefined));

      const promise = (service as any).processarInspecaoLancamento(mockItem);
      await drenarPendencias();
      const request = httpMock.expectOne(`${environment.apiBaseUrl}/v1/sst/inspecoes`);
      request.flush({
        id: 'inspecao-1', idInspetor: 'inspetor-1', status: 'CONCLUIDA',
        dataRealizacao: '2026-07-31T08:30:01-03:00', respostas: [],
        desvios: [{ id: 'desvio-1', idPergunta: 'p1', idOpcaoResposta: 'r1' }],
        evidencias: []
      });
      await promise;

      expect(mockOutboxService.enqueue).toHaveBeenCalledWith({
        endpoint: INSPECAO_ANEXO_ENDPOINT,
        method: 'POST',
        payload: { idInspecao: 'inspecao-1', idDesvio: 'desvio-1', anexo }
      });
    });
  });

  describe('processarAnexoInspecao', () => {
    it('envia o arquivo como multipart com nome e contexto preservados', async () => {
      const arquivo = new File(['foto'], 'camera.jpg', { type: 'image/jpeg' });
      const anexo = {
        idLocal: 'anexo-1', chaveDesvioLocal: 'p1:r1', contexto: 'DESVIO',
        arquivo, nomeArquivo: 'camera.jpg', contentType: 'image/jpeg'
      };
      const item = {
        id: 'out-anexo',
        endpoint: INSPECAO_ANEXO_ENDPOINT,
        method: 'POST',
        payload: {
          idInspecao: 'inspecao-1',
          idDesvio: 'desvio-1',
          anexo
        },
        status: 'PENDENTE'
      };
      mockDbService.getInspecao.and.returnValue(Promise.resolve({
        id: 'inspecao-1',
        status: 'CONCLUIDA',
        statusSync: 'SINCRONIZADO',
        anexosPendentes: [anexo]
      }));

      const promise = (service as any).processarAnexoInspecao(item);
      const request = httpMock.expectOne(req =>
        req.url === `${environment.apiBaseUrl}/v1/sst/inspecoes/inspecao-1/desvios/desvio-1/anexos`
        && req.params.get('contexto') === 'DESVIO'
      );
      expect(request.request.body instanceof FormData).toBeTrue();
      const enviado = request.request.body.get('arquivo') as File;
      expect(enviado.name).toBe('camera.jpg');
      expect(enviado.type).toBe('image/jpeg');
      request.flush({ id: 'anexo-remoto-1' });
      await promise;

      expect(mockDbService.saveInspecao).toHaveBeenCalledWith(jasmine.objectContaining({
        id: 'inspecao-1',
        anexosPendentes: []
      }));
      expect(mockOutboxService.discard).toHaveBeenCalledWith('out-anexo');
    });
  });
});
