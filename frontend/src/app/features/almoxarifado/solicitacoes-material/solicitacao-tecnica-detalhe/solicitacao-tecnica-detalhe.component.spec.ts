import '@angular/compiler';
import { CUSTOM_ELEMENTS_SCHEMA, NO_ERRORS_SCHEMA, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { describe, beforeEach, it, expect, vi } from 'vitest';
import { MessageService, ConfirmationService } from 'primeng/api';
import { NotificationService } from '@ces/ui';
import { SolicitacaoTecnicaDetalheComponent } from './solicitacao-tecnica-detalhe.component';
import { SolicitacaoMaterialTecnicaService } from '../../../../core/services/solicitacao-material-tecnica.service';
import { ObrasService } from '../../../../core/services/obras.service';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { RhService } from '../../../../core/services/rh.service';
import { AuthService } from '../../../../core/services/auth.service';
import { UsuarioLookupService } from '../../../../core/services/usuario-lookup.service';
import { ConnectivityService } from '../../../../core/services/connectivity.service';
import { SolicitacaoMaterialDetalheResponse } from '../../../../core/models/solicitacao-material-tecnica.models';

try {
  TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
} catch {}

const material = { id: 'mat-1', codigo: 'CABO-001', descricao: 'Cabo', unidadeMedida: 'M', detalhaSerie: false, ativo: true } as any;
const technical = {
  tipoTecnico: 'ADICAO_COMUM', tipoObra: 'OBRA', idObra: 'obra-1', ocorrencia: null,
  idAlmoxarifadoAtendimento: 'almox-1', idEquipe: 'equipe-1', dataPrevista: '2026-08-30', observacao: null,
  status: 'APROVADO', statusSeparacao: 'PRONTO_PARA_SEPARACAO', versao: 2,
  acoesPermitidas: ['EDITAR_PLANEJAMENTO', 'REGISTRAR_RESERVA', 'INICIAR_SEPARACAO', 'ESTORNAR', 'ENCERRAR_SALDO', 'VER_HISTORICO'], atrasada: false,
  reserva: null, encerradaEm: null, motivoEncerramento: null,
  totais: { solicitada: 1, entregaBruta: 0, entregaEstornada: 0, entregaLiquida: 0, devolucaoBruta: 0, devolucaoEstornada: 0, devolucaoLiquida: 0, pendente: 1 },
  itens: [{ id: 'item-1', material, quantidade: 1, quantidadeEntregue: 0, tipoMovimentacao: 'ADICAO_COMUM' }], operacoes: [],
} as any;
const detail: SolicitacaoMaterialDetalheResponse = { id: 'sol-1', contexto: 'TECNICO', status: 'APROVADO', versao: 2, acoesPermitidas: technical.acoesPermitidas, itens: technical.itens, tecnico: technical } as any;

describe('SolicitacaoTecnicaDetalheComponent v2', () => {
  let fixture: ComponentFixture<SolicitacaoTecnicaDetalheComponent>;
  let service: { buscarPorId: ReturnType<typeof vi.fn>; historico: ReturnType<typeof vi.fn> };
  let almoxarifadoService: {
    listarMateriais: ReturnType<typeof vi.fn>;
    listarEstoquesAtivos: ReturnType<typeof vi.fn>;
    buscarEstoquePorId: ReturnType<typeof vi.fn>;
    listarMotivosRejeicaoAtivos: ReturnType<typeof vi.fn>;
    listarMotivosPendencia: ReturnType<typeof vi.fn>;
    rejeitarSolicitacao: ReturnType<typeof vi.fn>;
  };
  const notifications = { error: vi.fn(), warning: vi.fn(), success: vi.fn() };

  beforeEach(async () => {
    TestBed.resetTestingModule();
    service = {
      buscarPorId: vi.fn().mockReturnValue(of(detail)),
      historico: vi.fn().mockReturnValue(of({
        eventos: [],
        integridade: [],
        page: { number: 0, size: 20, totalElements: 0, totalPages: 1 },
      })),
    };
    almoxarifadoService = {
      listarMateriais: vi.fn().mockReturnValue(of({ content: [] })),
      listarEstoquesAtivos: vi.fn().mockReturnValue(of({ content: [] })),
      buscarEstoquePorId: vi.fn().mockReturnValue(of({ id: 'almox-1', nomeIdentificador: 'Almoxarifado' })),
      listarMotivosRejeicaoAtivos: vi.fn().mockReturnValue(of({ content: [{ id: 'mot-1', nome: 'Material Indisponível', descricao: 'Sem estoque', ativo: true }] })),
      listarMotivosPendencia: vi.fn().mockReturnValue(of([])),
      rejeitarSolicitacao: vi.fn().mockReturnValue(of(undefined)),
    };
    await TestBed.configureTestingModule({
      imports: [SolicitacaoTecnicaDetalheComponent],
      schemas: [CUSTOM_ELEMENTS_SCHEMA, NO_ERRORS_SCHEMA],
      providers: [provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'sol-1' }, queryParamMap: { get: () => null } } } },
        { provide: SolicitacaoMaterialTecnicaService, useValue: service },
        { provide: ObrasService, useValue: { buscarObraPorId: vi.fn().mockReturnValue(of({ id: 'obra-1', numero: '10', titulo: 'Obra' })), listarAgentesAtivos: vi.fn().mockReturnValue(of([])) } },
        { provide: AlmoxarifadoService, useValue: almoxarifadoService },
         { provide: RhService, useValue: {
           listarEquipes: vi.fn().mockReturnValue(of({ content: [] })),
           listarColaboradoresParaSelecao: vi.fn().mockReturnValue(of({ content: [] })),
           listarMembrosAtivos: vi.fn().mockReturnValue(of([])),
         } },
         { provide: AuthService, useValue: { currentUser: signal({ permissoes: ['estoque:solicitacao:read'] }) } },
         { provide: ConnectivityService, useValue: { isOnline: signal(true) } },
        { provide: NotificationService, useValue: notifications },
        { provide: MessageService, useValue: { add: vi.fn() } }, { provide: ConfirmationService, useValue: { confirm: vi.fn() } },
        { provide: UsuarioLookupService, useValue: { resolverNome: vi.fn((value: string) => value) } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(SolicitacaoTecnicaDetalheComponent); fixture.detectChanges();
  });

  it('carrega o detalhe atômico pelo id da rota', () => { expect(service.buscarPorId).toHaveBeenCalledWith('sol-1'); expect(fixture.componentInstance['detail']()?.id).toBe('sol-1'); });
  it('usa o tipo e as ações v2 sem identidade de agregador', () => { expect(fixture.componentInstance['technical']()?.tipoTecnico).toBe('ADICAO_COMUM'); expect(fixture.componentInstance['can']('EDITAR_PLANEJAMENTO')).toBe(true); });
  it('informa no cabeçalho somente quando a solicitação está encerrada', () => {
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('A solicitação já foi encerrada.');

    fixture.componentInstance['detail'].set({
      ...detail,
      tecnico: {
        ...technical,
        encerrada: true,
        encerradaEm: '2026-09-21T10:00:00Z',
      },
    } as any);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'A solicitação já foi encerrada.',
    );
  });
  it('renderiza as ações devolvidas pelo backend sem exigir permissões locais duplicadas', () => {
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(text).toContain('REPLANEJAR');
    expect(text).toContain('INICIAR SEPARAÇÃO');
    expect(text).not.toContain('DEFINIR EQUIPE');
    expect(text).not.toContain('REGISTRAR RESERVA');
  });
  it('encaminha o clique do botão PrimeNG para o comando de planejamento', () => {
    const button = [...fixture.nativeElement.querySelectorAll('button')]
      .find((item) => item.textContent?.includes('REPLANEJAR')) as HTMLButtonElement | undefined;
    const openPlanning = vi.spyOn(fixture.componentInstance as any, 'openPlanning');

    expect(button).toBeDefined();
    button?.click();

    expect(openPlanning).toHaveBeenCalledTimes(1);
  });
  it('renderiza o diálogo de planejamento após o clique sem depender de trocar o foco', () => {
    const button = [...fixture.nativeElement.querySelectorAll('button')]
      .find((item) => item.textContent?.includes('REPLANEJAR')) as HTMLButtonElement | undefined;

    expect(button).toBeDefined();
    button?.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-solicitacao-tecnica-planejamento')).not.toBeNull();
  }, 15000);
  it('abre o diálogo de reserva quando acionado programaticamente', () => {
    fixture.componentInstance['openAction']('reserve');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-solicitacao-tecnica-acao')).not.toBeNull();
  });
  it('abre o diálogo de equipe quando acionado programaticamente', () => {
    fixture.componentInstance['openTeamAssignment']();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-solicitacao-tecnica-equipe')).not.toBeNull();
  });
  it('usa a ação de planejamento do backend para liberar o atalho de equipe', () => {
    expect(fixture.componentInstance['canQuickAssignTeam']()).toBe(true);
  });
  it('encaminha o clique do cabeçalho do histórico para o accordion', () => {
    const header = fixture.nativeElement.querySelector('p-accordion-header') as HTMLElement | null;

    expect(header).toBeDefined();
    header?.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-solicitacao-tecnica-historico')).not.toBeNull();
  });
  it('respeita a ausência da ação de separação para uma devolução', () => { fixture.componentInstance['detail'].set({ ...detail, tecnico: { ...technical, tipoTecnico: 'DEVOLUCAO_COMUM', statusSeparacao: null, acoesPermitidas: ['EDITAR_PLANEJAMENTO', 'VER_HISTORICO'] } }); expect(fixture.componentInstance['can']('INICIAR_SEPARACAO')).toBe(false); });
  it('abre o diálogo de planejamento pelo comando permitido', () => {
    fixture.componentInstance['openPlanning']();

    expect(fixture.componentInstance['planningVisible']()).toBe(true);
  });
  it.each(['reserve', 'deliver', 'return', 'reverse', 'close'] as const)('abre o diálogo para a ação %s', (mode) => {
    if (mode === 'return') {
      fixture.componentInstance['detail'].set({ ...detail, tecnico: { ...technical, tipoTecnico: 'DEVOLUCAO_COMUM', acoesPermitidas: [...technical.acoesPermitidas, 'CONFIRMAR_DEVOLUCAO'] } });
    } else if (mode === 'deliver') {
      fixture.componentInstance['detail'].set({ ...detail, tecnico: { ...technical, acoesPermitidas: [...technical.acoesPermitidas, 'ENTREGAR'] } });
    }
    fixture.componentInstance['openAction'](mode);

    expect(fixture.componentInstance['selectedAction']()).toBe(mode);
    expect(fixture.componentInstance['actionVisible']()).toBe(true);
  });

  it('respeita as ações devolvidas pelo backend após uma entrega realizada', () => {
    const detailComEntrega: SolicitacaoMaterialDetalheResponse = {
      ...detail,
      status: 'ENTREGUE_PARCIAL',
      itens: [{ ...detail.itens[0], quantidadeEntregue: 1 }],
      tecnico: {
        ...technical,
        statusSeparacao: 'SEPARADO',
        dataPrevista: '2026-09-01',
        totais: { ...technical.totais, entregaBruta: 1, entregaLiquida: 1 },
        operacoes: [{ id: 'op-1', tipo: 'ENTREGA', realizadaEm: '2026-08-30', itens: [] }],
        acoesPermitidas: ['ENTREGAR'],
      },
    } as any;

    fixture.componentInstance['detail'].set(detailComEntrega);
    fixture.detectChanges();

    // Permissões lógicas
    expect(fixture.componentInstance['can']('EDITAR_PLANEJAMENTO')).toBe(false);
    expect(fixture.componentInstance['can']('REGISTRAR_RESERVA')).toBe(false);
    expect(fixture.componentInstance['can']('ALTERAR_RESERVA')).toBe(false);
    expect(fixture.componentInstance['canQuickAssignTeam']()).toBe(false);

    // Tentativas de abertura
    fixture.componentInstance['openPlanning']();
    expect(fixture.componentInstance['planningVisible']()).toBe(false);

    fixture.componentInstance['openTeamAssignment']();
    expect(fixture.componentInstance['teamVisible']()).toBe(false);

    fixture.componentInstance['openAction']('reserve');
    expect(fixture.componentInstance['actionVisible']()).toBe(false);
  });

  it('mantém a ação conforme o backend, mesmo quando o saldo local diverge', () => {
    const detailSemSaldoPendente: SolicitacaoMaterialDetalheResponse = {
      ...detail,
      status: 'ENTREGUE',
      itens: [{ ...detail.itens[0], quantidade: 1, quantidadeEntregue: 1, quantidadePendente: 0 }],
      tecnico: {
        ...technical,
        totais: { ...technical.totais, solicitada: 1, entregaBruta: 1, entregaLiquida: 1, pendente: 0 },
        acoesPermitidas: ['ENTREGAR', 'VER_HISTORICO'],
      },
    } as any;

    fixture.componentInstance['detail'].set(detailSemSaldoPendente);
    fixture.detectChanges();

    expect(fixture.componentInstance['can']('ENTREGAR')).toBe(true);

    // A decisão de disponibilidade veio de acoesPermitidas.
    fixture.componentInstance['openAction']('deliver');
    expect(fixture.componentInstance['actionVisible']()).toBe(true);

    // O backend deve evitar enviar a ação em um estado inválido.
    const deliverButton = [...fixture.nativeElement.querySelectorAll('button')]
      .find((b) => b.textContent?.includes('CONFIRMAR ENTREGA'));
    expect(deliverButton).toBeDefined();
  });

  it('inicia com a sanfona maior minimizada por padrão e permite alternar sanfonas e sub-sanfonas', () => {
    const detailComOperacoes: SolicitacaoMaterialDetalheResponse = {
      ...detail,
      tecnico: {
        ...technical,
        operacoes: [
          {
            id: 'op-1',
            tipo: 'ENTREGA',
            realizadaEm: '2026-08-31T10:00:00Z',
            idEquipe: 'equipe-1',
            idUsuarioOperador: 'user-1',
            itens: [
              {
                id: 'it-op-1',
                idItemSolicitacao: 'item-1',
                idMaterial: 'mat-1',
                codigoMaterial: 'CABO-001',
                descricaoMaterial: 'Cabo',
                quantidade: 1,
              },
            ],
          },
        ],
      },
    } as any;

    fixture.componentInstance['detail'].set(detailComOperacoes);
    fixture.detectChanges();

    // Minimizada por padrão (sanfona maior e sub-sanfonas)
    expect(fixture.componentInstance['operationsOpen']()).toBe(false);
    expect(fixture.componentInstance['operationsSummary']()).toBe('1 entrega realizada');
    expect(fixture.componentInstance['isOperationExpanded']('op-1')).toBe(false);

    // Ao abrir a sanfona maior, sub-sanfonas continuam colapsadas até o usuário abrir
    fixture.componentInstance['toggleOperations']();
    expect(fixture.componentInstance['operationsOpen']()).toBe(true);
    expect(fixture.componentInstance['isOperationExpanded']('op-1')).toBe(false);

    // Permite abrir a sub-sanfona individualmente
    fixture.componentInstance['toggleOperation']('op-1');
    expect(fixture.componentInstance['isOperationExpanded']('op-1')).toBe(true);

    // Permite fechar a sub-sanfona
    fixture.componentInstance['toggleOperation']('op-1');
    expect(fixture.componentInstance['isOperationExpanded']('op-1')).toBe(false);

    // Permite expandir todas
    fixture.componentInstance['expandAllOperations']();
    expect(fixture.componentInstance['isOperationExpanded']('op-1')).toBe(true);

    // Permite recolher todas
    fixture.componentInstance['collapseAllOperations']();
    expect(fixture.componentInstance['isOperationExpanded']('op-1')).toBe(false);

    // Textos amigáveis
    expect(fixture.componentInstance['operationFriendlyTitle']('ENTREGA')).toBe('Entrega de Materiais');
    expect(fixture.componentInstance['operationFriendlyTitle']('DEVOLUCAO')).toBe('Devolução de Materiais ao Almoxarifado');
    expect(fixture.componentInstance['operationFriendlyTitle']('ESTORNO')).toBe('Correção / Estorno de Movimentação');
  });

  it('renderiza o termo de responsabilidade e os campos formais de assinatura (Almoxarifado e Equipe)', () => {
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(text).toContain('Termo de Responsabilidade & Assinaturas Formais');
    expect(text).toContain('Almoxarifado');
    expect(text).toContain('Equipe Receptora');
  });

  it('calcula dataEntrega e reflete a data formatada na assinatura quando entregue', () => {
    // Inicialmente sem entrega
    expect(fixture.componentInstance['dataEntrega']()).toBeNull();
    let text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Data: ____/____/________');

    // Com dataEntrega no detalhe
    fixture.componentInstance['detail'].set({
      ...detail,
      dataEntrega: '2026-09-20T14:30:00',
      tecnico: {
        ...technical,
        operacoes: [],
      },
    } as any);
    fixture.detectChanges();

    expect(fixture.componentInstance['dataEntrega']()).toBe('2026-09-20T14:30:00');
    text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('20/09/2026 14:30');

    // Com dataEntrega na operação de ENTREGA
    fixture.componentInstance['detail'].set({
      ...detail,
      dataEntrega: null,
      tecnico: {
        ...technical,
        operacoes: [{
          id: 'op-1',
          tipo: 'ENTREGA',
          realizadaEm: '2026-09-22T09:15:00',
          itens: [],
        }],
      },
    } as any);
    fixture.detectChanges();

    expect(fixture.componentInstance['dataEntrega']()).toBe('2026-09-22T09:15:00');
    text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('22/09/2026 09:15');
  });

  it('exibe o responsavel planejado e o responsavel efetivo de cada entrega', () => {
    fixture.componentInstance['detail'].set({
      ...detail,
      tecnico: {
        ...technical,
        idColaboradorSaque: 'colab-1',
        saqueForaEquipe: false,
        operacoes: [{
          id: 'op-1',
          tipo: 'ENTREGA',
          realizadaEm: '2026-08-31T10:00:00Z',
          idEquipe: 'equipe-1',
          idColaboradorSaque: 'colab-2',
          saqueForaEquipe: true,
          idUsuarioOperador: 'user-1',
          itens: [],
        }],
      },
    } as any);
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Responsável');
    expect(text).toContain('colab-1');
    expect(text).toContain('Saque:');
    expect(text).toContain('colab-2');
    expect(text).toContain('fora da equipe');
  });

  it('bloqueia e esconde a acao de replanejar quando a solicitacao possui movimentacao de entrega', () => {
    // Sem movimentacao e com permissao EDITAR_PLANEJAMENTO
    fixture.componentInstance['detail'].set({
      ...detail,
      acoesPermitidas: ['EDITAR_PLANEJAMENTO'],
      tecnico: {
        ...technical,
        operacoes: [],
      },
    } as any);
    fixture.detectChanges();
    expect(fixture.componentInstance['canPlanning']()).toBe(true);

    // Com movimentacao de ENTREGA registrada
    fixture.componentInstance['detail'].set({
      ...detail,
      acoesPermitidas: ['EDITAR_PLANEJAMENTO'],
      tecnico: {
        ...technical,
        operacoes: [{
          id: 'op-1',
          tipo: 'ENTREGA',
          realizadaEm: '2026-08-31T10:00:00Z',
          itens: [],
        }],
      },
    } as any);
    fixture.detectChanges();
    expect(fixture.componentInstance['canPlanning']()).toBe(false);
  });

  it.each([null, { id: 'reserva-1', numero: '123' }])('exibe rejeitar depois de iniciar separacao com reserva %j', (reserva) => {
    const acoesPermitidas = ['INICIAR_SEPARACAO', 'REJEITAR'];
    fixture.componentInstance['detail'].set({
      ...detail, acoesPermitidas,
      tecnico: { ...technical, reserva, acoesPermitidas },
    } as any);
    fixture.detectChanges();
    const labels = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'))
      .map(button => button.textContent?.trim());
    const iniciar = labels.indexOf('INICIAR SEPARAÇÃO');
    const pendencia = labels.indexOf('PENDÊNCIA');
    expect(iniciar).toBeGreaterThanOrEqual(0);
    expect(pendencia).toBe(iniciar + 1);
    expect(labels.indexOf('REJEITAR')).toBe(pendencia + 1);
  });

  it('permite abrir o modal de pendência da solicitação antes da rejeição', () => {
    fixture.componentInstance['detail'].set({
      ...detail,
      acoesPermitidas: ['REJEITAR'],
      tecnico: {
        ...technical,
        acoesPermitidas: ['REJEITAR'],
      },
    } as any);
    fixture.detectChanges();

    expect(fixture.componentInstance['canRegistrarPendencia']()).toBe(true);
    const labels = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'))
      .map(button => button.textContent?.trim());
    expect(labels.indexOf('PENDÊNCIA')).toBe(labels.indexOf('REJEITAR') - 1);

    fixture.componentInstance['abrirModalPendenciaSolicitacao']();
    expect(fixture.componentInstance['pendenciaModalVisible']()).toBe(true);
  });

  it('permite abrir o modal de rejeicao quando a acao REJEITAR esta permitida', () => {
    fixture.componentInstance['detail'].set({
      ...detail,
      acoesPermitidas: ['REJEITAR'],
      tecnico: {
        ...technical,
        acoesPermitidas: ['REJEITAR'],
      },
    } as any);
    fixture.detectChanges();

    expect(fixture.componentInstance['can']('REJEITAR')).toBe(true);
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('REJEITAR');

    fixture.componentInstance['rejectSolicitacao']();
    expect(almoxarifadoService.listarMotivosRejeicaoAtivos).toHaveBeenCalled();
    expect(fixture.componentInstance['displayRejectModal']()).toBe(true);
    expect(fixture.componentInstance['motivosRejeicaoOptions']().length).toBe(1);
  });

  it('valida campos obrigatorios antes de confirmar a rejeicao', () => {
    fixture.componentInstance['rejectionData'].idMotivoRejeicao = '';
    fixture.componentInstance['rejectionData'].justificativa = '';
    fixture.componentInstance['confirmRejection']();
    expect(notifications.warning).toHaveBeenCalledWith(
      'Motivo e descrição/justificativa (mínimo de 5 caracteres) são obrigatórios.',
    );
    expect(almoxarifadoService.rejeitarSolicitacao).not.toHaveBeenCalled();

    fixture.componentInstance['rejectionData'].idMotivoRejeicao = 'mot-1';
    fixture.componentInstance['rejectionData'].justificativa = 'abc';
    fixture.componentInstance['confirmRejection']();
    expect(notifications.warning).toHaveBeenCalled();
    expect(almoxarifadoService.rejeitarSolicitacao).not.toHaveBeenCalled();
  });

  it('executa a rejeicao com sucesso e atualiza o estado', () => {
    fixture.componentInstance['displayRejectModal'].set(true);
    fixture.componentInstance['rejectionData'].idMotivoRejeicao = 'mot-1';
    fixture.componentInstance['rejectionData'].justificativa = 'Material em falta no estoque';

    fixture.componentInstance['confirmRejection']();

    expect(almoxarifadoService.rejeitarSolicitacao).toHaveBeenCalledWith('sol-1', {
      idMotivoRejeicao: 'mot-1',
      justificativa: 'Material em falta no estoque',
    });
    expect(notifications.success).toHaveBeenCalledWith('Solicitação técnica rejeitada com sucesso.');
    expect(fixture.componentInstance['displayRejectModal']()).toBe(false);
  });

  it('exibe o banner de solicitacao rejeitada com o motivo e justificativa', () => {
    fixture.componentInstance['detail'].set({
      ...detail,
      status: 'REJEITADO',
      motivoRejeicao: {
        id: 'mot-1',
        nome: 'Cancelado pelo solicitante',
        descricao: 'Cancelamento a pedido da obra',
        ativo: true,
      },
      justificativaRejeicao: 'Obra paralisada temporariamente',
      tecnico: {
        ...technical,
        status: 'REJEITADO',
      },
    } as any);
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Informações da Rejeição');
    expect(text).toContain('Cancelado pelo solicitante');
    expect(text).toContain('Obra paralisada temporariamente');
  });
});
