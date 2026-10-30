import '@angular/compiler';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { NotificationService } from '@ces/ui';
import { SolicitacaoMaterialDetalheResponse } from '../../../../core/models/solicitacao-material-tecnica.models';
import { ObrasService } from '../../../../core/services/obras.service';
import { RhService } from '../../../../core/services/rh.service';
import { SolicitacaoMaterialTecnicaService } from '../../../../core/services/solicitacao-material-tecnica.service';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { SolicitacaoTecnicaAcaoComponent } from './solicitacao-tecnica-acao.component';
import { LookupStore } from '../../../../core/lookup/store/lookup.store';

try {
  TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
} catch {}

describe('SolicitacaoTecnicaAcaoComponent', () => {
  let fixture: ComponentFixture<SolicitacaoTecnicaAcaoComponent>;
  let component: SolicitacaoTecnicaAcaoComponent;
  let registrarReserva: ReturnType<typeof vi.fn>;
  let entregar: ReturnType<typeof vi.fn>;
  let devolver: ReturnType<typeof vi.fn>;
  let listarSaldosNegativos: ReturnType<typeof vi.fn>;
  const notifications = {
    error: vi.fn(),
    warning: vi.fn(),
    success: vi.fn(),
  };

  const criarDetalhe = (reserva: Record<string, unknown> | null = null) =>
    ({
      id: 'sol-1',
      status: 'APROVADO',
      itens: [],
      tecnico: {
        tipoTecnico: 'ADICAO_COMUM',
        tipoObra: 'OBRA',
        idObra: 'obra-1',
        ocorrencia: null,
        idAlmoxarifadoAtendimento: 'almox-1',
        idEquipe: 'eq-1',
        dataPrevista: '2026-09-15',
        observacao: null,
        statusSeparacao: 'SEPARADO',
        reserva,
        totais: {
          solicitada: 0,
          entregaBruta: 0,
          entregaEstornada: 0,
          entregaLiquida: 0,
          devolucaoBruta: 0,
          devolucaoEstornada: 0,
          devolucaoLiquida: 0,
          pendente: 0,
        },
        operacoes: [],
        versao: 4,
        acoesPermitidas: ['REGISTRAR_RESERVA', 'ALTERAR_RESERVA'],
        atrasada: false,
        encerradaEm: null,
        idUsuarioEncerramento: null,
        motivoEncerramento: null,
      },
      contexto: 'TECNICO',
      versao: 4,
      statusSeparacao: 'SEPARADO',
      acoesPermitidas: ['REGISTRAR_RESERVA', 'ALTERAR_RESERVA'],
    }) as SolicitacaoMaterialDetalheResponse;

  const configurar = async (detail = criarDetalhe()) => {
    TestBed.resetTestingModule();
    registrarReserva = vi.fn(() => of({}));
    entregar = vi.fn(() => of({ idOperacao: 'op-1' }));
    devolver = vi.fn(() => of({ idOperacao: 'op-return-1' }));
    listarSaldosNegativos = vi.fn(() => of([]));
    vi.clearAllMocks();

    TestBed.configureTestingModule({
      imports: [SolicitacaoTecnicaAcaoComponent],
      providers: [
        {
          provide: AlmoxarifadoService,
          useValue: {
            listarMotivosPendencia: () => of([]),
            registrarPendencia: () => of(undefined),
            removerPendencia: () => of(undefined),
            listarSaldosNegativos,
          },
        },
        {
          provide: SolicitacaoMaterialTecnicaService,
          useValue: { registrarReserva, entregar, devolver },
        },
        {
          provide: ObrasService,
          useValue: {
            listarAgentesAtivos: () =>
              of([{ id: 'agent-1', nome: 'Ana Souza', empresaOrgao: 'Concessionária', ativo: true }]),
            buscarObraPorId: () =>
              of({ id: 'obra-1', numero: 'OB-100', titulo: 'Obra Central', tipoObra: 'OBRA' }),
          },
        },
        {
          provide: RhService,
          useValue: {
            listarColaboradoresParaSelecao: () =>
              of({
                content: [
                  {
                    id: 'colab-1',
                    primeiroNome: 'Ana',
                    ultimoNome: 'Souza',
                    nomeCompleto: 'Ana Souza',
                    cpf: '00000000000',
                    ativo: true,
                  },
                  {
                    id: 'colab-2',
                    primeiroNome: 'Bruno',
                    ultimoNome: 'Lima',
                    nomeCompleto: 'Bruno Lima',
                    cpf: '11111111111',
                    ativo: true,
                  },
                ],
                page: { number: 0, size: 2000, totalElements: 2, totalPages: 1 },
              }),
            listarMembrosAtivos: () =>
              of([
                {
                  id: 'member-1',
                  idColaborador: 'colab-1',
                  nomeColaborador: 'Ana Souza',
                  funcao: 'RESPONSAVEL',
                  vinculoAtivo: true,
                },
              ]),
            buscarColaboradorPorId: (id: string) =>
              of({ id, nomeCompleto: id === 'colab-1' ? 'Ana Souza' : 'Bruno Lima', ativo: true }),
            listarEquipes: () =>
              of({
                content: [
                  {
                    id: 'eq-1',
                    codigo: 'EQ-01',
                    ativo: true,
                    tipoEquipe: { id: 'te-1', nome: 'Linha Viva' },
                  },
                ],
                page: { number: 0, size: 1000, totalElements: 1, totalPages: 1 },
              }),
          },
        },
        {
          provide: LookupStore,
          useValue: {
            searchCollaborators: () => of({ items: [], nextCursor: null, hasMore: false }),
          },
        },
        { provide: NotificationService, useValue: notifications },
      ],
    });
    TestBed.overrideComponent(SolicitacaoTecnicaAcaoComponent, {
      set: {
        imports: [],
        template: `
          <span>{{ title() }}</span>
          <span>Identifique a reserva</span>
          <span>{{ technical().reserva ? 'Explique a alteração' : 'Adicione uma observação' }}</span>
          @for (agent of agents(); track agent.value) { <span>{{ agent.label }}</span> }
        `,
      },
    });
    await TestBed.compileComponents();

    fixture = TestBed.createComponent(SolicitacaoTecnicaAcaoComponent);
    component = fixture.componentInstance;
    (component as any).detail = signal(detail);
    (component as any).mode = signal('reserve');
    (component as any).visible = signal(true);
    fixture.detectChanges();
  };

  beforeEach(() => {
    notifications.error.mockClear();
    notifications.warning.mockClear();
    notifications.success.mockClear();
  });

  it('começa com a data vazia e orienta o usuário em duas etapas', async () => {
    await configurar();

    expect(component['reserveDate']()).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Identifique a reserva');
    expect(fixture.nativeElement.textContent).toContain('Adicione uma observação');
    expect(fixture.nativeElement.textContent).toContain('Ana Souza');
  });

  it('valida campos obrigatórios antes de enviar', async () => {
    await configurar();

    component['submit']();

    expect(component['fieldError']('numero')).toBeTruthy();
    expect(component['fieldError']('dataCriacao')).toBeTruthy();
    expect(registrarReserva).not.toHaveBeenCalled();
  });

  it('envia a reserva com data civil e agente selecionado', async () => {
    await configurar();

    component['reserveNumber'].set('000123');
    component['reserveDate'].set(new Date(2026, 8, 10));
    component['agentId'].set('agent-1');
    component['submit']();

    expect(registrarReserva).toHaveBeenCalledWith(
      'sol-1',
      expect.objectContaining({
        versaoEsperada: 4,
        numero: '000123',
        dataCriacao: '2026-09-10',
        idAgenteResponsavel: 'agent-1',
      }),
      expect.any(String),
    );
  });

  it('omite agente quando o campo opcional fica vazio', async () => {
    await configurar();

    component['reserveNumber'].set('123');
    component['reserveDate'].set(new Date(2026, 8, 10));
    component['agentId'].set(null);
    component['submit']();

    const request = registrarReserva.mock.calls[0][1] as Record<string, unknown>;
    expect(request).not.toHaveProperty('idAgenteResponsavel');
  });

  it('recarrega os dados da reserva ao reabrir o formulário', async () => {
    const detail = criarDetalhe({
      id: 'reserva-1',
      idSolicitacao: 'sol-1',
      numero: '98765',
      dataCriacao: '2026-08-20',
      idAgenteResponsavel: 'agent-1',
    });
    await configurar(detail);

    component['reserveNumber'].set('alterado-sem-salvar');
    component.visible.set(false);
    fixture.detectChanges();
    component.visible.set(true);
    fixture.detectChanges();
    (component as any).resetFormForCurrentMode(true);
    fixture.detectChanges();

    expect(component['reserveNumber']()).toBe('98765');
    expect(component['reserveDate']()).toEqual(new Date(2026, 7, 20));
    expect(component['justification']()).toBe('');
  });

  it('avisa, sem bloquear, os materiais com saldo negativo na equipe que recebe', async () => {
    const detail = criarDetalhe() as any;
    detail.itens = [
      {
        id: 'item-medidor',
        material: { id: 'mat-medidor', codigo: '410001', descricao: 'MEDIDOR MONOFÁSICO', detalhaSerie: false, ativo: true },
        quantidade: 3,
        quantidadeEntregue: 0,
        quantidadePendente: 3,
        tipoMovimentacao: 'ADICAO_COMUM',
      },
    ];
    detail.tecnico.idEquipe = 'eq-1';
    detail.tecnico.acoesPermitidas = ['ENTREGAR'];
    detail.acoesPermitidas = ['ENTREGAR'];
    await configurar(detail);
    listarSaldosNegativos.mockReturnValue(
      of([
        { id: 's1', material: { id: 'mat-medidor', codigo: '410001', descricao: 'MEDIDOR MONOFÁSICO', unidadeMedida: 'UN' }, quantidade: -2 },
      ]),
    );
    (component as any).mode = signal('deliver');
    (component as any).resetFormForCurrentMode();

    expect(listarSaldosNegativos).toHaveBeenCalledWith({ idEquipe: 'eq-1' });
    expect(component['idsMateriaisEntrega']()).toEqual(['mat-medidor']);
    expect(component['saldoNegativoDoMaterial']('mat-medidor')).toEqual(
      expect.objectContaining({ quantidade: -2, unidadeMedida: 'UN' }),
    );

    listarSaldosNegativos.mockReturnValue(of([]));
    component['onDeliveryTeamChange']('eq-2');

    expect(listarSaldosNegativos).toHaveBeenLastCalledWith({ idEquipe: 'eq-2' });
    expect(component['saldoNegativoDoMaterial']('mat-medidor')).toBeNull();
  });

  it('preenche todos os saldos ao selecionar entrega total', async () => {
    const detail = criarDetalhe() as any;
    detail.itens = [
      {
        id: 'item-comum',
        material: {
          id: 'material-comum',
          codigo: 'MAT-1',
          descricao: 'Material comum',
          detalhaSerie: false,
          ativo: true,
        },
        quantidade: 5,
        quantidadeEntregue: 2,
        quantidadePendente: 3,
        tipoMovimentacao: 'ADICAO_COMUM',
      },
      {
        id: 'item-seriado',
        material: {
          id: 'material-seriado',
          codigo: 'MAT-2',
          descricao: 'Material seriado',
          detalhaSerie: true,
          ativo: true,
        },
        quantidade: 2,
        quantidadeEntregue: 0,
        quantidadePendente: 2,
        tipoMovimentacao: 'ADICAO_COMUM',
      },
    ];
    detail.tecnico.idEquipe = 'eq-1';
    detail.tecnico.totais.pendente = 5;
    detail.tecnico.acoesPermitidas = ['ENTREGAR'];
    detail.acoesPermitidas = ['ENTREGAR'];
    await configurar(detail);
    (component as any).mode = signal('deliver');
    (component as any).resetFormForCurrentMode();

    component['selectDeliveryMode']('all');

    expect(component['deliveryMode']()).toBe('all');
    expect(
      component['lines']().filter((line: any) => line.idItemSolicitacao === 'item-comum'),
    ).toEqual([expect.objectContaining({ quantity: 3 })]);
    expect(
      component['lines']().filter((line: any) => line.idItemSolicitacao === 'item-seriado'),
    ).toHaveLength(2);
    expect(
      component['lines']()
        .filter((line: any) => line.idItemSolicitacao === 'item-seriado')
        .every((line: any) => line.quantity === 1),
    ).toBe(true);
  });

  it('inicia a entrega parcial com assinaturas físicas e permite completar um material', async () => {
    const detail = criarDetalhe() as any;
    detail.itens = [
      {
        id: 'item-1',
        material: {
          id: 'material-1',
          codigo: 'MAT-1',
          descricao: 'Material comum',
          detalhaSerie: false,
          ativo: true,
        },
        quantidade: 4,
        quantidadeEntregue: 1,
        quantidadePendente: 3,
        tipoMovimentacao: 'ADICAO_COMUM',
      },
    ];
    detail.tecnico.idEquipe = 'eq-1';
    detail.tecnico.reserva = { id: 'res-1', numero: '12345', dataCriacao: '2026-09-15' };
    detail.tecnico.totais.pendente = 3;
    detail.tecnico.acoesPermitidas = ['ENTREGAR'];
    detail.acoesPermitidas = ['ENTREGAR'];
    await configurar(detail);
    (component as any).mode = signal('deliver');
    (component as any).resetFormForCurrentMode();

    expect(component['deliveryMode']()).toBe('partial');
    expect(component['signatureWarehouse']()).toBe('FISICA');
    expect(component['signatureTeam']()).toBe('FISICA');

    component['toggleItemFullySelected'](0, true);
    component['submit']();

    expect(entregar).toHaveBeenCalledWith(
      'sol-1',
      expect.objectContaining({
        assinaturaAlmox: 'FISICA',
        assinaturaEquipe: 'FISICA',
        itens: [expect.objectContaining({ idItemSolicitacao: 'item-1', quantidade: 3 })],
      }),
      expect.any(String),
    );
  });

  it('permite e envia múltiplos números de série para o mesmo idItemSolicitacao', async () => {
    const detail = criarDetalhe() as any;
    detail.itens = [
      {
        id: '666c1532-d5d3-4235-aabf-2fd290ba28ae',
        material: {
          id: 'mat-seriado',
          codigo: 'SER-1',
          descricao: 'Material com série',
          detalhaSerie: true,
          ativo: true,
        },
        quantidade: 3,
        quantidadeEntregue: 0,
        quantidadePendente: 3,
        tipoMovimentacao: 'ADICAO_COMUM',
      },
    ];
    detail.tecnico.idEquipe = 'b7ef1a42-bc9d-4660-a618-a9f1ca01fbda';
    detail.tecnico.reserva = { id: 'res-1', numero: '12345', dataCriacao: '2026-09-15' };
    detail.tecnico.totais.pendente = 3;
    detail.tecnico.versao = 2;
    detail.tecnico.acoesPermitidas = ['ENTREGAR'];
    detail.acoesPermitidas = ['ENTREGAR'];
    await configurar(detail);
    (component as any).mode = signal('deliver');
    (component as any).resetFormForCurrentMode();

    component['selectDeliveryMode']('all');
    const lines = component['lines']();
    expect(lines).toHaveLength(3);

    component['updateLine'](0, { numeroSerie: '12354215' });
    component['updateLine'](1, { numeroSerie: '32634523' });
    component['updateLine'](2, { numeroSerie: '4523744525' });

    component['submit']();

    expect(entregar).toHaveBeenCalledWith(
      'sol-1',
      {
        versaoEsperada: 2,
        idEquipe: 'b7ef1a42-bc9d-4660-a618-a9f1ca01fbda',
        assinaturaAlmox: 'FISICA',
        assinaturaEquipe: 'FISICA',
        itens: [
          {
            idItemSolicitacao: '666c1532-d5d3-4235-aabf-2fd290ba28ae',
            quantidade: 1,
            numeroSerie: '12354215',
          },
          {
            idItemSolicitacao: '666c1532-d5d3-4235-aabf-2fd290ba28ae',
            quantidade: 1,
            numeroSerie: '32634523',
          },
          {
            idItemSolicitacao: '666c1532-d5d3-4235-aabf-2fd290ba28ae',
            quantidade: 1,
            numeroSerie: '4523744525',
          },
        ],
      },
      expect.any(String),
    );
  });

  it('bloqueia o envio se houver números de série repetidos na requisição', async () => {
    const detail = criarDetalhe() as any;
    detail.itens = [
      {
        id: 'item-seriado-1',
        material: {
          id: 'mat-1',
          codigo: 'SER-1',
          descricao: 'Material com série',
          detalhaSerie: true,
          ativo: true,
        },
        quantidade: 2,
        quantidadeEntregue: 0,
        quantidadePendente: 2,
        tipoMovimentacao: 'ADICAO_COMUM',
      },
    ];
    detail.tecnico.idEquipe = 'eq-1';
    detail.tecnico.totais.pendente = 2;
    detail.tecnico.acoesPermitidas = ['ENTREGAR'];
    detail.acoesPermitidas = ['ENTREGAR'];
    await configurar(detail);
    (component as any).mode = signal('deliver');
    (component as any).resetFormForCurrentMode();

    component['selectDeliveryMode']('all');
    component['updateLine'](0, { numeroSerie: 'SERIE-DUPLICADA' });
    component['updateLine'](1, { numeroSerie: 'SERIE-DUPLICADA' });

    component['submit']();

    expect(component['fieldError']('itens')).toBe(
      'Não repita o mesmo número de série na requisição.',
    );
    expect(entregar).not.toHaveBeenCalled();
  });

  it('bloqueia o avanço de etapa se a soma das quantidades exceder o saldo pendente', async () => {
    const detail = criarDetalhe() as any;
    detail.itens = [
      {
        id: 'item-1',
        material: {
          id: 'mat-1',
          codigo: 'MAT-1',
          descricao: 'Material',
          detalhaSerie: false,
          ativo: true,
        },
        quantidade: 2,
        quantidadeEntregue: 0,
        quantidadePendente: 2,
        tipoMovimentacao: 'ADICAO_COMUM',
      },
    ];
    detail.tecnico.idEquipe = 'eq-1';
    detail.tecnico.totais.pendente = 2;
    detail.tecnico.acoesPermitidas = ['ENTREGAR'];
    detail.acoesPermitidas = ['ENTREGAR'];
    await configurar(detail);
    (component as any).mode = signal('deliver');
    (component as any).resetFormForCurrentMode();

    component['updateLine'](0, { quantity: 5 });

    const step1Valid = component['validateDeliverStep1']();
    expect(step1Valid).toBe(false);
    expect(component['fieldError']('itens')).toBe('Uma quantidade excede o saldo permitido.');
  });

  it('visualiza o responsavel planejado e omite os campos quando a entrega nao o altera', async () => {
    const detail = criarDetalhe() as any;
    detail.itens = [
      {
        id: 'item-1',
        material: {
          id: 'material-1',
          codigo: 'MAT-1',
          descricao: 'Material',
          detalhaSerie: false,
          ativo: true,
        },
        quantidade: 1,
        quantidadeEntregue: 0,
        quantidadePendente: 1,
        tipoMovimentacao: 'ADICAO_COMUM',
      },
    ];
    detail.tecnico.idColaboradorSaque = 'colab-1';
    detail.tecnico.saqueForaEquipe = false;
    detail.tecnico.reserva = { id: 'res-1', numero: '12345', dataCriacao: '2026-09-15' };
    detail.tecnico.totais.pendente = 1;
    detail.tecnico.acoesPermitidas = ['ENTREGAR'];
    detail.acoesPermitidas = ['ENTREGAR'];
    await configurar(detail);
    (component as any).mode = signal('deliver');
    (component as any).resetFormForCurrentMode();

    expect(component['plannedWithdrawalName']()).toBe('Ana Souza');
    expect(component['withdrawalCollaboratorId']()).toBe('colab-1');
    component['toggleItemFullySelected'](0, true);
    component['submit']();

    const request = entregar.mock.calls[0][1] as Record<string, unknown>;
    expect(request).not.toHaveProperty('idColaboradorSaque');
    expect(request).not.toHaveProperty('saqueForaEquipe');
  });

  it('envia colaborador substituto e saque fora da equipe na confirmacao', async () => {
    const detail = criarDetalhe() as any;
    detail.itens = [
      {
        id: 'item-1',
        material: {
          id: 'material-1',
          codigo: 'MAT-1',
          descricao: 'Material',
          detalhaSerie: false,
          ativo: true,
        },
        quantidade: 1,
        quantidadeEntregue: 0,
        quantidadePendente: 1,
        tipoMovimentacao: 'ADICAO_COMUM',
      },
    ];
    detail.tecnico.idColaboradorSaque = 'colab-1';
    detail.tecnico.saqueForaEquipe = false;
    detail.tecnico.reserva = { id: 'res-1', numero: '12345', dataCriacao: '2026-09-15' };
    detail.tecnico.totais.pendente = 1;
    detail.tecnico.acoesPermitidas = ['ENTREGAR'];
    detail.acoesPermitidas = ['ENTREGAR'];
    await configurar(detail);
    (component as any).mode = signal('deliver');
    (component as any).resetFormForCurrentMode();

    component['onWithdrawalOutsideTeamChange'](true);
    component['onWithdrawalCollaboratorChange']('colab-2');
    component['toggleItemFullySelected'](0, true);
    component['submit']();

    expect(entregar).toHaveBeenCalledWith(
      'sol-1',
      expect.objectContaining({ idColaboradorSaque: 'colab-2', saqueForaEquipe: true }),
      expect.any(String),
    );
  });

  it('bloqueia a confirmação de entrega se a reserva não estiver preenchida em obra comum', async () => {
    const detail = criarDetalhe() as any;
    detail.itens = [
      {
        id: 'item-1',
        material: {
          id: 'material-1',
          codigo: 'MAT-1',
          descricao: 'Material',
          detalhaSerie: false,
          ativo: true,
        },
        quantidade: 1,
        quantidadeEntregue: 0,
        quantidadePendente: 1,
        tipoMovimentacao: 'ADICAO_COMUM',
      },
    ];
    detail.tecnico.idEquipe = 'eq-1';
    detail.tecnico.tipoObra = 'OBRA';
    detail.tecnico.ocorrencia = null;
    detail.tecnico.reserva = null;
    detail.tecnico.totais.pendente = 1;
    detail.tecnico.acoesPermitidas = ['ENTREGAR'];
    detail.acoesPermitidas = ['ENTREGAR'];
    await configurar(detail);
    (component as any).mode = signal('deliver');
    (component as any).resetFormForCurrentMode();

    component['toggleItemFullySelected'](0, true);
    component['submit']();

    expect(notifications.warning).toHaveBeenCalledWith(
      expect.stringContaining('reserva SAP são obrigatórios'),
    );
    expect(entregar).not.toHaveBeenCalled();
  });

  it('permite confirmar a entrega sem reserva em obra corretiva (OC/OS)', async () => {
    const detail = criarDetalhe() as any;
    detail.itens = [
      {
        id: 'item-1',
        material: {
          id: 'material-1',
          codigo: 'MAT-1',
          descricao: 'Material',
          detalhaSerie: false,
          ativo: true,
        },
        quantidade: 1,
        quantidadeEntregue: 0,
        quantidadePendente: 1,
        tipoMovimentacao: 'ADICAO_COMUM',
      },
    ];
    detail.tecnico.idEquipe = 'eq-1';
    detail.tecnico.tipoObra = 'CORRETIVA';
    detail.tecnico.ocorrencia = 'OC-99999';
    detail.tecnico.reserva = null;
    detail.tecnico.totais.pendente = 1;
    detail.tecnico.acoesPermitidas = ['ENTREGAR'];
    detail.acoesPermitidas = ['ENTREGAR'];
    await configurar(detail);
    (component as any).mode = signal('deliver');
    (component as any).resetFormForCurrentMode();

    component['toggleItemFullySelected'](0, true);
    component['submit']();

    expect(entregar).toHaveBeenCalledWith(
      'sol-1',
      expect.objectContaining({
        itens: [expect.objectContaining({ idItemSolicitacao: 'item-1', quantidade: 1 })],
      }),
      expect.any(String),
    );
  });

  it('na entrega parcial de material seriado, preencher apenas o numero de serie atribui quantidade 1 e permite avancar para a etapa 2', async () => {
    const detail = criarDetalhe() as any;
    detail.itens = [
      {
        id: 'item-seriado-1',
        material: {
          id: 'mat-1',
          codigo: 'SER-1',
          descricao: 'Material com série',
          detalhaSerie: true,
          ativo: true,
        },
        quantidade: 2,
        quantidadeEntregue: 0,
        quantidadePendente: 2,
        tipoMovimentacao: 'ADICAO_COMUM',
      },
    ];
    detail.tecnico.idEquipe = 'eq-1';
    detail.tecnico.reserva = { id: 'res-1', numero: '12345', dataCriacao: '2026-09-15' };
    detail.tecnico.totais.pendente = 2;
    detail.tecnico.acoesPermitidas = ['ENTREGAR'];
    detail.acoesPermitidas = ['ENTREGAR'];
    await configurar(detail);
    (component as any).mode = signal('deliver');
    (component as any).resetFormForCurrentMode();

    expect(component['deliveryMode']()).toBe('partial');
    const initialLines = component['lines']();
    expect(initialLines).toHaveLength(1);
    expect(initialLines[0].serialRequired).toBe(true);
    expect(initialLines[0].quantity).toBeNull();

    // O usuário digita apenas o número de série no campo de texto
    component['updateLine'](0, { numeroSerie: 'SN-998877' });

    // A linha deve ter quantidade 1 automaticamente
    expect(component['lines']()[0].quantity).toBe(1);
    expect(component['lines']()[0].numeroSerie).toBe('SN-998877');

    // Ao chamar nextStep, a etapa 1 deve ser válida e avançar para etapa 2
    component['nextStep']();
    expect(component['activeStep']()).toBe(2);
    expect(component['fieldError']('itens')).toBeUndefined();
  });

  it('mantém o mesmo uid da linha ao atualizar o número de série para evitar recriação do DOM e perda de foco', async () => {
    const detail = criarDetalhe() as any;
    detail.itens = [
      {
        id: 'item-seriado-1',
        material: {
          id: 'mat-1',
          codigo: 'SER-1',
          descricao: 'Material com série',
          detalhaSerie: true,
          ativo: true,
        },
        quantidade: 1,
        quantidadeEntregue: 0,
        quantidadePendente: 1,
        tipoMovimentacao: 'ADICAO_COMUM',
      },
    ];
    detail.tecnico.idEquipe = 'eq-1';
    detail.tecnico.reserva = { id: 'res-1', numero: '12345', dataCriacao: '2026-09-15' };
    detail.tecnico.totais.pendente = 1;
    detail.tecnico.acoesPermitidas = ['ENTREGAR'];
    detail.acoesPermitidas = ['ENTREGAR'];
    await configurar(detail);
    (component as any).mode = signal('deliver');
    (component as any).resetFormForCurrentMode();

    const originalUid = component['lines']()[0].uid;
    expect(originalUid).toBeTruthy();

    component['updateLine'](0, { numeroSerie: 'A' });
    expect(component['lines']()[0].uid).toBe(originalUid);

    component['updateLine'](0, { numeroSerie: 'AB' });
    expect(component['lines']()[0].uid).toBe(originalUid);
    expect(component['trackByLineUid'](0, component['lines']()[0])).toBe(originalUid);
  });

  it('ao limpar o número de série na entrega parcial, a quantidade retorna para nula se não estiver totalmente selecionado', async () => {
    const detail = criarDetalhe() as any;
    detail.itens = [
      {
        id: 'item-seriado-1',
        material: {
          id: 'mat-1',
          codigo: 'SER-1',
          descricao: 'Material com série',
          detalhaSerie: true,
          ativo: true,
        },
        quantidade: 2,
        quantidadeEntregue: 0,
        quantidadePendente: 2,
        tipoMovimentacao: 'ADICAO_COMUM',
      },
    ];
    detail.tecnico.idEquipe = 'eq-1';
    detail.tecnico.reserva = { id: 'res-1', numero: '12345', dataCriacao: '2026-09-15' };
    detail.tecnico.totais.pendente = 2;
    detail.tecnico.acoesPermitidas = ['ENTREGAR'];
    detail.acoesPermitidas = ['ENTREGAR'];
    await configurar(detail);
    (component as any).mode = signal('deliver');
    (component as any).resetFormForCurrentMode();

    component['updateLine'](0, { numeroSerie: 'SN-123' });
    expect(component['lines']()[0].quantity).toBe(1);

    component['updateLine'](0, { numeroSerie: '' });
    expect(component['lines']()[0].quantity).toBeNull();
  });

  it('envia a condicao selecionada por item na devolucao tecnica', async () => {
    const detail = criarDetalhe() as any;
    detail.itens = [{
      id: 'item-return-1',
      material: {
        id: 'mat-return-1', codigo: 'MAT-RET', descricao: 'Material devolvido',
        detalhaSerie: false, ativo: true,
      },
      quantidade: 2,
      quantidadeEntregue: 0,
      quantidadePendente: 2,
      tipoMovimentacao: 'DEVOLUCAO_COMUM',
    }];
    detail.tecnico.tipoTecnico = 'DEVOLUCAO_COMUM';
    detail.tecnico.idEquipe = 'eq-1';
    detail.tecnico.totais.pendente = 2;
    detail.tecnico.acoesPermitidas = ['CONFIRMAR_DEVOLUCAO'];
    detail.acoesPermitidas = ['CONFIRMAR_DEVOLUCAO'];
    await configurar(detail);
    (component as any).mode = signal('return');
    (component as any).resetFormForCurrentMode();

    component['updateLine'](0, { quantity: 1, condition: 'SUCATA' });
    component['submit']();

    expect(devolver).toHaveBeenCalledWith(
      'sol-1',
      expect.objectContaining({
        idEquipe: 'eq-1',
        itens: [expect.objectContaining({
          idItemSolicitacao: 'item-return-1', quantidade: 1, condicao: 'SUCATA',
        })],
      }),
      expect.any(String),
    );
  });

  it('envia a devolucao com reserva opcional quando informada', async () => {
    const detail = criarDetalhe() as any;
    detail.itens = [{
      id: 'item-return-1',
      material: { id: 'mat-1', codigo: 'CABO-1', descricao: 'Cabo', detalhaSerie: false, ativo: true },
      quantidade: 2, quantidadeEntregue: 0, quantidadePendente: 2, tipoMovimentacao: 'DEVOLUCAO_COMUM',
    }];
    detail.tecnico.tipoTecnico = 'DEVOLUCAO_COMUM';
    detail.tecnico.idEquipe = 'eq-1';
    detail.tecnico.reserva = null;
    detail.tecnico.totais.pendente = 2;
    detail.tecnico.acoesPermitidas = ['CONFIRMAR_DEVOLUCAO'];
    detail.acoesPermitidas = ['CONFIRMAR_DEVOLUCAO'];
    await configurar(detail);
    (component as any).mode = signal('return');
    (component as any).resetFormForCurrentMode();

    component['updateLine'](0, { quantity: 1, condition: 'NOVO' });
    component['reserveNumber'].set('987654');
    component['submit']();

    expect(devolver).toHaveBeenCalledWith(
      'sol-1',
      expect.objectContaining({
        idEquipe: 'eq-1',
        numeroReserva: '987654',
        itens: [expect.objectContaining({
          idItemSolicitacao: 'item-return-1', quantidade: 1, condicao: 'NOVO',
        })],
      }),
      expect.any(String),
    );
  });

  it('selectSignatureType FISICA preenche ambos os lados com FISICA e envia a entrega', async () => {
    const detail = criarDetalhe() as any;
    detail.itens = [{
      id: 'item-1',
      material: { id: 'mat-1', codigo: 'CABO-1', descricao: 'Cabo', detalhaSerie: false, ativo: true },
      quantidade: 1, quantidadeEntregue: 0, quantidadePendente: 1, tipoMovimentacao: 'ADICAO_COMUM',
    }];
    detail.tecnico.idEquipe = 'eq-1';
    detail.tecnico.reserva = { id: 'res-1', numero: '12345', dataCriacao: '2026-09-15' };
    detail.tecnico.totais.pendente = 1;
    detail.tecnico.acoesPermitidas = ['ENTREGAR'];
    detail.acoesPermitidas = ['ENTREGAR'];
    await configurar(detail);
    (component as any).mode = signal('deliver');
    (component as any).resetFormForCurrentMode();

    // Configura estado mínimo para submit direto após assinatura física
    component['deliveryMode'].set('all');
    component['withdrawalCollaboratorId'].set('colab-1');
    component['lines'].set([{
      uid: 'l1',
      idItemSolicitacao: 'item-1',
      label: 'CABO-1 · Cabo',
      max: 1,
      quantity: 1,
      serialRequired: false,
      numeroSerie: '',
      condition: 'NOVO',
    }]);

    // Abre o modal de assinatura manualmente (simula a ação do usuário)
    component['displaySignatureTypeModal'].set(true);
    expect(component['displaySignatureTypeModal']()).toBe(true);

    // Seleciona assinatura física
    component['selectSignatureType']('FISICA');

    expect(component['signatureWarehouse']()).toBe('FISICA');
    expect(component['signatureTeam']()).toBe('FISICA');
    expect(component['displaySignatureTypeModal']()).toBe(false);
    expect(entregar).toHaveBeenCalledWith(
      'sol-1',
      expect.objectContaining({
        assinaturaAlmox: 'FISICA',
        assinaturaEquipe: 'FISICA',
      }),
      expect.any(String),
    );
  });

  it('gerencia o ciclo de abertura, fechamento e manipulação de assinaturas no painel digital', async () => {
    const detail = criarDetalhe() as any;
    await configurar(detail);
    (component as any).mode = signal('deliver');
    (component as any).resetFormForCurrentMode();

    component['selectSignatureType']('DIGITAL');
    expect(component['displayDigitalSignatureDashboard']()).toBe(true);
    expect(component['displaySignatureTypeModal']()).toBe(false);

    // Abre o canvas para o almoxarife: esconde o dashboard
    component['openSignaturePad']('almox');
    expect(component['currentSignatureField']()).toBe('almox');
    expect(component['displaySignatureModal']()).toBe(true);
    expect(component['displayDigitalSignatureDashboard']()).toBe(false);

    // Fecha o pad de assinatura: reabre o dashboard
    component['closeSignaturePad']();
    expect(component['displaySignatureModal']()).toBe(false);
    expect(component['displayDigitalSignatureDashboard']()).toBe(true);

    // Helpers de assinatura física e limpeza pontual
    component['setPhysicalSignature']('almox');
    expect(component['signatureWarehouse']()).toBe('FISICA');
    component['clearSignatureData']('almox');
    expect(component['signatureWarehouse']()).toBe('');

    component['setPhysicalSignature']('equipe');
    expect(component['signatureTeam']()).toBe('FISICA');
    component['clearSignatureData']('equipe');
    expect(component['signatureTeam']()).toBe('');
  });

  it('abre o modal de pendência do material na entrega ao acionar openItemPendencia', () => {
    component['openItemPendencia']({
      uid: 'line-1',
      idItemSolicitacao: 'item-100',
      label: 'CAB-01 — Cabo Alumínio',
      max: 10,
      quantity: 5,
      serialRequired: false,
      numeroSerie: '',
      condition: 'NOVO',
      motivoPendencia: null,
      justificativaPendencia: null,
    });

    expect(component['pendenciaModalVisible']()).toBe(true);
    expect(component['pendenciaModalItemId']()).toBe('item-100');
    expect(component['pendenciaModalItemLabel']()).toBe('CAB-01 — Cabo Alumínio');
  });

  it('abre o modal de pendência da solicitação ao acionar abrirModalPendencia', () => {
    component['abrirModalPendencia']();

    expect(component['pendenciaModalVisible']()).toBe(true);
    expect(component['pendenciaModalItemId']()).toBeNull();
    expect(component['pendenciaModalItemLabel']()).toBeNull();
  });
});
