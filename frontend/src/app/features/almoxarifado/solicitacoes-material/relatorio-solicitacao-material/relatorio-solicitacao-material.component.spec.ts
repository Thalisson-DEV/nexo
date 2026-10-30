import { NO_ERRORS_SCHEMA, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { describe, beforeEach, it, expect, vi } from 'vitest';
import { MessageService, ConfirmationService } from 'primeng/api';
import { NotificationService } from '@ces/ui';

import { RelatorioSolicitacaoMaterialComponent } from './relatorio-solicitacao-material.component';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { SolicitacaoMaterialTecnicaService } from '../../../../core/services/solicitacao-material-tecnica.service';
import { LookupStore } from '../../../../core/lookup/store/lookup.store';
import { RhService } from '../../../../core/services/rh.service';
import { ObrasService } from '../../../../core/services/obras.service';
import { AuthService } from '../../../../core/services/auth.service';
import { ConnectivityService } from '../../../../core/services/connectivity.service';
import { UsuarioLookupService } from '../../../../core/services/usuario-lookup.service';
import { SolicitacaoResponse } from '../../../../core/models/almoxarifado.models';

try {
  TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
} catch {}

const mockSolicitacao: SolicitacaoResponse = {
  id: 'sol-comercial-12345678',
  status: 'APROVADO',
  dataSolicitacao: '2026-09-15T10:00:00Z',
  dataVerificacao: '2026-09-15T14:00:00Z',
  dataEntrega: undefined,
  idRequisitante: 'usr-1',
  idAprovador: 'usr-2',
  idEntregadorAlmox: 'usr-3',
  estoqueOrigem: { id: 'est-1', nomeIdentificador: 'Almox Central' } as any,
  estoqueDestino: { id: 'est-2', nomeIdentificador: 'Equipe Alpha' } as any,
  itens: [
    {
      id: 'item-1',
      material: {
        id: 'mat-1',
        codigo: 'CAB-01',
        descricao: 'Cabo de Alumínio',
        unidadeMedida: 'M',
        detalhaSerie: false,
        ativo: true,
      } as any,
      quantidade: 100,
      quantidadeEntregue: 60,
      quantidadeEntregueBruta: 75,
      quantidadeEntregaEstornada: 15,
      quantidadeEntregaLiquida: 60,
      quantidadePendente: 40,
    },
  ],
  assinaturaAlmox: undefined,
  assinaturaEquipe: undefined,
};

describe('RelatorioSolicitacaoMaterialComponent', () => {
  const usuarioLogado = signal<{ permissoes: string[] }>({ permissoes: ['estoque:solicitacao:write'] });
  let component: RelatorioSolicitacaoMaterialComponent;
  let fixture: ComponentFixture<RelatorioSolicitacaoMaterialComponent>;
  let almoxarifadoServiceMock: any;
  let solicitacaoTecnicaServiceMock: any;
  let confirmationServiceMock: any;

  beforeEach(async () => {
    usuarioLogado.set({ permissoes: ['estoque:solicitacao:write'] });
    almoxarifadoServiceMock = {
      buscarSolicitacaoPorId: vi.fn().mockReturnValue(of(mockSolicitacao)),
      buscarConsumoAnterior: vi.fn().mockReturnValue(
        of({
          idSolicitacao: 'sol-comercial-12345678',
          idEstoque: 'est-2',
          consultadoAte: '2026-10-02T10:00:00Z',
          itens: [
            {
              idMaterial: 'mat-1',
              ultimaSolicitacao: { id: 'sol-ant', dataSolicitacao: '2026-09-01T10:00:00Z', dataEntrega: '2026-09-02T10:00:00Z', quantidadeSolicitada: 40, quantidadeRecebida: 40 },
              quantidadeAplicada: 39,
              quantidadeDevolvida: 1,
              saldoAtual: 0,
            },
          ],
        }),
      ),
      listarUsuariosAtivos: vi.fn().mockReturnValue(
        of({
          content: [
            { id: 'usr-1', login: 'joao.silva' },
            { id: 'usr-2', login: 'maria.santos' },
            { id: 'usr-3', login: 'carlos.almox' },
          ],
        }),
      ),
      listarMotivosRejeicaoAtivos: vi.fn().mockReturnValue(of({ content: [] })),
      listarMotivosPendencia: vi.fn().mockReturnValue(of([])),
      listarSeriais: vi.fn().mockReturnValue(of({ content: [] })),
      buscarMaterialPorId: vi.fn().mockReturnValue(of(null)),
      aprovarSolicitacao: vi.fn().mockReturnValue(of(mockSolicitacao)),
      registrarEntrega: vi.fn().mockReturnValue(of(mockSolicitacao)),
      rejeitarSolicitacao: vi.fn().mockReturnValue(of(mockSolicitacao)),
      estornarParcial: vi.fn().mockReturnValue(of(mockSolicitacao)),
      listarSaldosNegativos: vi.fn().mockReturnValue(of([])),
      listarSaldos: vi.fn().mockReturnValue(of({ content: [{ quantidade: 120 }] })),
      definirSeriesReservadas: vi.fn((_: string, __: string, series: string[]) => of(series)),
      listarEstoquesAtivos: vi.fn().mockReturnValue(
        of({
          content: [
            { id: 'est-1', nomeIdentificador: 'Almox Central', ativo: true, tipoEstoque: { nome: 'FISICO' } },
            { id: 'est-3', nomeIdentificador: 'Almox Norte', ativo: true, tipoEstoque: { nome: 'FISICO' } },
            { id: 'est-2', nomeIdentificador: 'Equipe Alpha', ativo: true, tipoEstoque: { nome: 'CAMPO' } },
          ],
        }),
      ),
    };

    solicitacaoTecnicaServiceMock = {
      historico: vi.fn().mockReturnValue(
        of({
          eventos: [],
          integridade: [],
          page: { number: 0, size: 20, totalElements: 0, totalPages: 1 },
        }),
      ),
    };

    const lookupStoreMock = {
      searchMaterials: vi.fn().mockReturnValue(of({ items: [], nextCursor: null })),
    };
    confirmationServiceMock = { confirm: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [RelatorioSolicitacaoMaterialComponent],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { params: { id: 'sol-comercial-12345678' } },
            params: of({ id: 'sol-comercial-12345678' }),
            queryParams: of({}),
          },
        },
        { provide: AlmoxarifadoService, useValue: almoxarifadoServiceMock },
        { provide: SolicitacaoMaterialTecnicaService, useValue: solicitacaoTecnicaServiceMock },
        { provide: LookupStore, useValue: lookupStoreMock },
        { provide: MessageService, useValue: { add: vi.fn() } },
        { provide: ConfirmationService, useValue: confirmationServiceMock },
        {
          provide: NotificationService,
          useValue: { error: vi.fn(), success: vi.fn(), warning: vi.fn() },
        },
        {
          provide: RhService,
          useValue: { listarEquipes: vi.fn().mockReturnValue(of({ content: [] })) },
        },
        { provide: ObrasService, useValue: { buscarObraPorId: vi.fn().mockReturnValue(of(null)) } },
        { provide: AuthService, useValue: { currentUser: usuarioLogado } },
        { provide: ConnectivityService, useValue: { isOnline: signal(true) } },
        { provide: UsuarioLookupService, useValue: { resolverNome: vi.fn((val) => val) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(RelatorioSolicitacaoMaterialComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('deve ser instanciado e carregar dados da solicitação', () => {
    expect(component).toBeTruthy();
    expect(almoxarifadoServiceMock.buscarSolicitacaoPorId).toHaveBeenCalledWith(
      'sol-comercial-12345678',
    );
  });

  it('mostra o consumo desde a última solicitação de cada material', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(almoxarifadoServiceMock.buscarConsumoAnterior).toHaveBeenCalledWith('sol-comercial-12345678');
    expect(el.textContent).toContain('Consumo desde a última solicitação');
    // Feedback rápido para quem aprova: não entra no documento impresso.
    expect(el.querySelector('app-consumo-anterior-tabela')?.parentElement?.classList).toContain('print:hidden');
    expect(el.textContent).toContain('02/09/2026');
    expect(el.textContent).toContain('Usou 98% do que pegou.');
  });

  it('deve renderizar o cabeçalho oficial da empresa', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('NOME DA EMPRESA');
    expect(el.textContent).toContain('Almoxarifado Central & Gestão de Materiais');
  });

  it('deve renderizar os dados gerais da solicitação em padrão .kv', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Almox Central');
    expect(el.textContent).toContain('Equipe Alpha');
    expect(el.textContent).toContain('joao.silva');
  });

  it('deve informar no cabeçalho somente quando a solicitação comercial estiver encerrada', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).not.toContain('A solicitação já foi encerrada.');

    component['solicitacao'].set({ ...mockSolicitacao, status: 'ENTREGUE' });
    fixture.detectChanges();

    expect(el.textContent).toContain('A solicitação já foi encerrada.');
  });

  it('deve incluir a sanfona de linha do tempo de alterações com o componente de histórico', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Linha do tempo de alterações');
    expect(el.querySelector('p-accordion')).not.toBeNull();
  });

  it('deve renderizar o quadro formal de assinaturas com as 2 vias', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Termo de Responsabilidade & Assinaturas Formais');
    expect(el.textContent).toContain('Almoxarifado');
    expect(el.textContent).toContain('Requisitante / Receptora');
  });

  it('deve retornar o tom semântico correto para os status', () => {
    expect(component['getStatusTone']('ENTREGUE')).toBe('success');
    expect(component['getStatusTone']('APROVADO')).toBe('info');
    expect(component['getStatusTone']('PENDENTE')).toBe('warning');
    expect(component['getStatusTone']('REJEITADO')).toBe('danger');
  });

  it('resume a solicitação em cards e numa faixa de situação, como o comprovante de movimentação', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Materiais');
    expect(el.textContent).toContain('Entregue');
    expect(el.textContent).toContain('75 entregues, 15 estornados');
    expect(el.textContent).toContain('Números de Série');
    expect(el.textContent).toContain('Saldo Pendente');
    expect(el.querySelector('.destaque')?.getAttribute('data-tone')).toBe('brand');
    expect(el.textContent).toContain('Aprovada, aguardando retirada');
    expect(component['totaisOperacionais']()).toEqual({
      solicitada: 100,
      entregaBruta: 75,
      entregaEstornada: 15,
      entregaLiquida: 60,
      pendente: 40,
    });
  });

  it('avisa na entrega os materiais com saldo negativo no estoque da equipe, sem bloquear', () => {
    almoxarifadoServiceMock.listarSaldosNegativos.mockReturnValue(
      of([
        { id: 's1', material: { id: 'mat-1', codigo: 'CAB-01', descricao: 'Cabo de Alumínio', unidadeMedida: 'M' }, quantidade: -4 },
        { id: 's2', material: { id: 'mat-1', codigo: 'CAB-01', descricao: 'Cabo de Alumínio', unidadeMedida: 'M' }, quantidade: -1.5 },
      ]),
    );

    component['startDelivery']();

    expect(almoxarifadoServiceMock.listarSaldosNegativos).toHaveBeenCalledWith({ idEstoque: 'est-2' });
    expect(component['idsMateriaisEntrega']()).toEqual(['mat-1']);
    expect(component['saldoNegativoDoMaterial']('mat-1')?.quantidade).toBe(-5.5);
    expect(component['isDeliveryMode']()).toBe(true);
  });

  it('não deve permitir remover material que já possui entrega registrada', () => {
    component['startDelivery']();

    const [linha] = component['entregaRascunho']();
    expect(linha).toMatchObject({ idItem: 'item-1', pendente: 40, quantidade: 40 });
    expect(component['podeTirarDaSolicitacao'](linha)).toBe(false);
    expect(component['canRemoveItem'](component['itemsDraft']()[0])).toBe(false);
  });

  it('mostra na entrega o saldo de cada material na origem', () => {
    component['startDelivery']();

    expect(almoxarifadoServiceMock.listarSaldos).toHaveBeenCalledWith(
      expect.objectContaining({ idEstoque: 'est-1', idMaterial: 'mat-1', condicao: 'NOVO' }),
    );
    expect(component['saldoOrigem']()['mat-1']).toBe(120);
  });

  it('deve enviar remoção e inclusão de material na mesma entrega', () => {
    component['confirmationService'] = confirmationServiceMock;
    component['solicitacao'].set({
      ...mockSolicitacao,
      itens: [
        {
          ...mockSolicitacao.itens[0],
          quantidadeEntregue: 0,
          quantidadeEntregueBruta: 0,
          quantidadeEntregaEstornada: 0,
          quantidadeEntregaLiquida: 0,
          quantidadePendente: 100,
        },
      ],
    });
    component['startDelivery']();
    component['alternarRemocaoEntrega'](0);
    component['selectedMaterial'].set({
      id: 'mat-2',
      codigo: 'MAT-02',
      descricao: 'Material comum substituto',
      unidadeMedida: 'UN',
      detalhaSerie: false,
      ativo: true,
    });
    component['itemQuantity'].set(5);
    component['addItem']();
    component['definirQuantidadeEntrega'](1, 3);
    component['deliveryData'].assinaturaAlmox = 'FISICA';
    component['deliveryData'].assinaturaEquipe = 'FISICA';
    component['withdrawalCollaboratorId'].set('colaborador-saque');

    component['executeDelivery']();
    confirmationServiceMock.confirm.mock.calls[0][0].accept();

    expect(almoxarifadoServiceMock.registrarEntrega).toHaveBeenCalledWith(
      'sol-comercial-12345678',
      expect.objectContaining({
        idColaboradorSaque: 'colaborador-saque',
        saqueForaEquipe: true,
        itens: [],
        itensAdicionados: [
          expect.objectContaining({
            idMaterial: 'mat-2',
            quantidade: 3,
            quantidadeSolicitada: 3,
          }),
        ],
        itensRemovidos: ['item-1'],
      }),
    );
  });

  it('na aprovação troca o almoxarifado de origem e a data do saque', () => {
    component['confirmationService'] = confirmationServiceMock;
    component['solicitacao'].set({ ...mockSolicitacao, status: 'PENDENTE', dataPrevista: '2026-10-20', itens: [{ ...mockSolicitacao.itens[0], quantidadeEntregaLiquida: 0 }] });
    component['startApproval']();

    expect(component['opcoesOrigem']().map((o) => o.value)).toEqual(['est-1', 'est-3']);
    component['trocarOrigem']('est-3');
    component['dataPrevistaEscolhida'].set(new Date(2026, 9, 24));
    component['confirmApproval']();
    confirmationServiceMock.confirm.mock.calls[0][0].accept();

    expect(almoxarifadoServiceMock.aprovarSolicitacao).toHaveBeenCalledWith(
      'sol-comercial-12345678',
      expect.objectContaining({ idEstoqueOrigem: 'est-3', dataPrevista: '2026-10-24' }),
    );
  });

  it('na aprovação sem mudança não manda origem nem data', () => {
    component['confirmationService'] = confirmationServiceMock;
    component['solicitacao'].set({ ...mockSolicitacao, status: 'PENDENTE', dataPrevista: '2026-10-20', itens: [{ ...mockSolicitacao.itens[0], quantidadeEntregaLiquida: 0 }] });
    component['startApproval']();
    component['confirmApproval']();
    confirmationServiceMock.confirm.mock.calls[0][0].accept();

    const pedido = almoxarifadoServiceMock.aprovarSolicitacao.mock.calls[0][1];
    expect(pedido.idEstoqueOrigem).toBeUndefined();
    expect(pedido.dataPrevista).toBeUndefined();
  });

  it('na entrega troca a origem, refaz os saldos e manda a nova origem', () => {
    component['confirmationService'] = confirmationServiceMock;
    component['solicitacao'].set({ ...mockSolicitacao, itens: [{ ...mockSolicitacao.itens[0], quantidadeEntregue: 0, quantidadeEntregaLiquida: 0, quantidadePendente: 100 }] });
    component['startDelivery']();
    almoxarifadoServiceMock.listarSaldos.mockClear();

    component['trocarOrigem']('est-3');

    expect(almoxarifadoServiceMock.listarSaldos).toHaveBeenCalledWith(expect.objectContaining({ idEstoque: 'est-3', idMaterial: 'mat-1' }));
    component['definirQuantidadeEntrega'](0, 10);
    component['deliveryData'].assinaturaAlmox = 'FISICA';
    component['deliveryData'].assinaturaEquipe = 'FISICA';
    component['withdrawalCollaboratorId'].set('colaborador-saque');
    component['executeDelivery']();
    confirmationServiceMock.confirm.mock.calls[0][0].accept();

    expect(almoxarifadoServiceMock.registrarEntrega).toHaveBeenCalledWith(
      'sol-comercial-12345678',
      expect.objectContaining({ idEstoqueOrigem: 'est-3' }),
    );
  });

  it('depois da primeira saída a origem não muda mais', () => {
    component['startDelivery']();
    expect(component['podeTrocarOrigem']()).toBe(false);
  });

  it('deve executar rejeição com sucesso ao preencher motivo e justificativa', () => {
    component['rejectionData'] = {
      idMotivoRejeicao: 'motivo-1',
      justificativa: 'Item incorreto para a obra',
    };
    component['displayRejectModal'].set(true);

    component['confirmRejection']();

    expect(almoxarifadoServiceMock.rejeitarSolicitacao).toHaveBeenCalledWith(
      'sol-comercial-12345678',
      {
        idMotivoRejeicao: 'motivo-1',
        justificativa: 'Item incorreto para a obra',
      },
    );
    expect(component['displayRejectModal']()).toBe(false);
  });

  it('deve exibir o tipo comercial fixado em 221', () => {
    expect(component['tipoLabel'](component['solicitacao']()?.tipoTecnico)).toBe('221 — Saída Direta / Obra');
    expect(component['tipoLabel']('ADICAO_COMUM')).toBe('221 — Saída Direta / Obra');
    expect(component['tipoLabel']('221')).toBe('221 — Saída Direta / Obra');
    expect(component['tipoTone'](component['solicitacao']()?.tipoTecnico)).toBe('info');
    expect(component['tipoIcon'](component['solicitacao']()?.tipoTecnico)).toBe('pi pi-shopping-cart');
  });

  it('pendência é do almoxarifado: sem write/plan o botão não aparece', () => {
    usuarioLogado.set({ permissoes: ['estoque:solicitacao:read', 'estoque:solicitacao:approve'] });
    expect(component['podeRegistrarPendencia']()).toBe(false);
  });

  it('permite abrir modal de pendência da solicitação antes de rejeitar', () => {
    expect(component['podeRegistrarPendencia']()).toBe(true);
    component['abrirModalPendenciaSolicitacao']();
    expect(component['pendenciaModalVisible']()).toBe(true);
    expect(component['pendenciaModalItemId']()).toBeNull();
  });

  it('junta as séries do mesmo material numa linha só, como no relatório de movimentações', () => {
    component['solicitacao'].set({
      ...mockSolicitacao,
      itens: [
        ...[1, 2, 3].map((n) => ({
          id: `med-${n}`,
          material: { id: 'mat-med', codigo: '802149', descricao: 'MEDIDOR', unidadeMedida: 'UN', detalhaSerie: true, ativo: true } as any,
          quantidade: 1,
          quantidadeEntregue: 1,
          quantidadeEntregaLiquida: 1,
          quantidadePendente: 0,
          numSerie: `52658${n}`,
        })),
        mockSolicitacao.itens[0],
      ],
    });
    fixture.detectChanges();

    const grupos = component['gruposItens']();
    expect(grupos).toHaveLength(2);
    expect(grupos[0]).toMatchObject({ solicitada: 3, entregue: 3, seriesEntregues: ['526581', '526582', '526583'] });
    expect(component['grupoExpansivel'](grupos[0])).toBe(true);
    expect(component['resumoSeries']()).toMatchObject({ entregues: 3, materiais: 1 });
  });

  it('define as séries antes da entrega e a entrega já vem com elas escolhidas', () => {
    const medidor = {
      id: 'med-1',
      material: { id: 'mat-med', codigo: '802149', descricao: 'MEDIDOR', unidadeMedida: 'UN', detalhaSerie: true, ativo: true } as any,
      quantidade: 3,
      quantidadeEntregue: 0,
      quantidadeEntregaLiquida: 0,
      quantidadePendente: 3,
    };
    component['solicitacao'].set({ ...mockSolicitacao, acoesPermitidas: ['ENTREGAR'], itens: [medidor] } as any);
    const [grupo] = component['gruposItens']();
    expect(component['podeDefinirSeries'](grupo)).toBe(true);

    component['abrirReserva'](grupo);
    component['reservaRascunho'].set(['S1', 'S2']);
    component['salvarReserva'](grupo);
    expect(almoxarifadoServiceMock.definirSeriesReservadas).toHaveBeenCalledWith('sol-comercial-12345678', 'med-1', ['S1', 'S2']);

    component['solicitacao'].set({ ...mockSolicitacao, itens: [{ ...medidor, seriesReservadas: ['S1', 'S2'] }] } as any);
    component['startDelivery']();
    expect(component['entregaRascunho']()[0]).toMatchObject({ series: ['S1', 'S2'], pendente: 3 });
  });

  it('permite abrir modal de pendência do material durante confirmação de entrega', () => {
    component['startDelivery']();
    expect(component['isDeliveryMode']()).toBe(true);

    const item = component['itemsDraft']()[0];
    component['abrirModalPendenciaItem'](item);
    expect(component['pendenciaModalVisible']()).toBe(true);
    expect(component['pendenciaModalItemId']()).toBe(item.id);
    expect(component['pendenciaModalItemLabel']()).toContain('CAB-01');
  });
});
