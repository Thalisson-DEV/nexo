import { CUSTOM_ELEMENTS_SCHEMA, NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { NotificationService } from '@ces/ui';

try {
  TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
} catch {}
import {
  KanbanSolicitacaoTecnicaResponse,
  SolicitacaoMaterialSummaryResponse,
} from '../../../../core/models/solicitacao-material-tecnica.models';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { ObrasService } from '../../../../core/services/obras.service';
import { RhService } from '../../../../core/services/rh.service';
import { SolicitacaoMaterialTecnicaService } from '../../../../core/services/solicitacao-material-tecnica.service';
import { UsuarioLookupService } from '../../../../core/services/usuario-lookup.service';
import { SolicitacoesTecnicasKanbanComponent } from './solicitacoes-tecnicas-kanban.component';

describe('SolicitacoesTecnicasKanbanComponent', () => {
  let component: SolicitacoesTecnicasKanbanComponent;
  let fixture: ComponentFixture<SolicitacoesTecnicasKanbanComponent>;

  const mockResponse: KanbanSolicitacaoTecnicaResponse = {
    colunas: [
      {
        etapa: 'PLANEJAMENTO',
        label: 'Planejamento',
        icone: 'pi pi-calendar',
        subColunas: [
          {
            subEtapa: 'PENDENTE_PLANEJAMENTO',
            label: 'Pendente de Planejamento',
            corPadrao: 'amber',
            itens: [],
            total: 3,
          },
          {
            subEtapa: 'PLANEJADO',
            label: 'Planejado',
            corPadrao: 'sky',
            itens: [],
            total: 2,
          },
        ],
        metricas: { total: 5, atrasadas: 1, foraPrazo: 0, diasMaisAntiga: 4 },
      },
      {
        etapa: 'SEPARACAO',
        label: 'Separação',
        icone: 'pi pi-box',
        subColunas: [
          {
            subEtapa: 'PRONTO_PARA_SEPARACAO',
            label: 'Pronto para Separação',
            corPadrao: 'orange',
            itens: [],
            total: 4,
          },
          {
            subEtapa: 'EM_SEPARACAO',
            label: 'Em Separação',
            corPadrao: 'purple',
            itens: [],
            total: 2,
          },
          {
            subEtapa: 'SEPARADO',
            label: 'Separado',
            corPadrao: 'emerald',
            itens: [],
            total: 1,
          },
        ],
        metricas: { total: 7, atrasadas: 0, foraPrazo: 1, diasMaisAntiga: 2 },
      },
      {
        etapa: 'ENTREGA',
        label: 'Entrega & Campo',
        icone: 'pi pi-truck',
        subColunas: [
          {
            subEtapa: 'PENDENTE_RETIRADA',
            label: 'Pendente de Retirada',
            corPadrao: 'cyan',
            itens: [],
            total: 2,
          },
          {
            subEtapa: 'PENDENTE_DEVOLUCAO',
            label: 'Pendente de Devolução',
            corPadrao: 'violet',
            itens: [],
            total: 1,
          },
          {
            subEtapa: 'CONCLUIDO',
            label: 'Concluído',
            corPadrao: 'green',
            itens: [],
            total: 10,
          },
        ],
        metricas: { total: 13, atrasadas: 0, foraPrazo: 0, diasMaisAntiga: null },
      },
      {
        etapa: 'RECUSADO',
        label: 'Recusado',
        icone: 'pi pi-times-circle',
        subColunas: [
          {
            subEtapa: 'REJEITADO',
            label: 'Rejeitado',
            corPadrao: 'rose',
            itens: [],
            total: 1,
          },
          {
            subEtapa: 'CANCELADO',
            label: 'Cancelado',
            corPadrao: 'slate',
            itens: [],
            total: 1,
          },
        ],
        metricas: { total: 2, atrasadas: 0, foraPrazo: 0, diasMaisAntiga: null },
      },
    ],
  };

  const obterKanban = vi.fn(() => of(mockResponse));
  const buscarPorId = vi.fn((id: string) =>
    of({
      id,
      versao: 1,
      status: 'APROVADO',
      itens: [],
      acoesPermitidas: ['EDITAR_PLANEJAMENTO', 'INICIAR_SEPARACAO', 'PULAR_SEPARACAO', 'CONCLUIR_SEPARACAO', 'ENTREGAR', 'REJEITAR'],
    })
  );
  const iniciarSeparacao = vi.fn(() => of('EM_SEPARACAO' as any));
  const concluirSeparacao = vi.fn(() => of('SEPARADO' as any));
  const pularSeparacao = vi.fn(() => of('SEPARADO' as any));

  const listarMotivosRejeicaoAtivos = vi.fn(() =>
    of({
      content: [
        { id: 'mot-1', nome: 'Material Indisponível', descricao: 'Falta no estoque central' },
        { id: 'mot-2', nome: 'Erro de Cadastro', descricao: 'Dados inconsistentes' },
      ],
    })
  );
  const rejeitarSolicitacao = vi.fn(() => of(undefined));

  const mockNotifications = {
    info: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
    success: vi.fn(),
  };

  const criarItemMock = (overrides: Partial<SolicitacaoMaterialSummaryResponse> = {}): SolicitacaoMaterialSummaryResponse => ({
    id: 'sol-12345678-abcd',
    tipo: 'ADICAO_COMUM',
    tipoObra: 'OBRA',
    idObra: 'obra-1',
    ocorrencia: null,
    contexto: 'TECNICO' as any,
    dataSolicitacao: '2026-10-01T08:00:00Z',
    idRequisitante: 'user-1',
    idAlmoxarifadoAtendimento: 'almox-1',
    status: 'APROVADO' as any,
    statusSeparacao: 'PRONTO_PARA_SEPARACAO' as any,
    quantidadeItens: 2,
    quantidadeSolicitada: 5,
    quantidadeEntregaLiquida: 0,
    quantidadeDevolucaoLiquida: 0,
    quantidadePendente: 5,
    totalOperacoes: 0,
    atrasada: false,
    versao: 1,
    acoesPermitidas: ['EDITAR_PLANEJAMENTO', 'INICIAR_SEPARACAO', 'PULAR_SEPARACAO', 'CONCLUIR_SEPARACAO', 'ENTREGAR', 'REJEITAR'],
    ...overrides,
  });

  beforeEach(async () => {
    obterKanban.mockClear();
    buscarPorId.mockClear();
    iniciarSeparacao.mockClear();
    concluirSeparacao.mockClear();
    pularSeparacao.mockClear();
    listarMotivosRejeicaoAtivos.mockClear();
    rejeitarSolicitacao.mockClear();
    mockNotifications.info.mockClear();
    mockNotifications.warning.mockClear();
    mockNotifications.error.mockClear();
    mockNotifications.success.mockClear();

    await TestBed.configureTestingModule({
      imports: [SolicitacoesTecnicasKanbanComponent],
      schemas: [CUSTOM_ELEMENTS_SCHEMA, NO_ERRORS_SCHEMA],
      providers: [
        provideRouter([]),
        {
          provide: SolicitacaoMaterialTecnicaService,
          useValue: {
            obterKanban,
            buscarPorId,
            iniciarSeparacao,
            concluirSeparacao,
            pularSeparacao,
          },
        },
        {
          provide: AlmoxarifadoService,
          useValue: {
            listarMotivosRejeicaoAtivos,
            rejeitarSolicitacao,
            listarEstoquesAtivos: vi.fn(() => of({ content: [] })),
            removerPendencia: vi.fn(() => of(undefined)),
          },
        },
        {
          provide: RhService,
          useValue: {
            buscarEquipePorId: vi.fn(() => of({ id: 'eq-1', codigo: 'EQ-01' })),
            listarEquipes: vi.fn(() => of({ content: [] })),
          },
        },
        {
          provide: ObrasService,
          useValue: {
            buscarObraPorId: vi.fn(() => of(null)),
          },
        },
        {
          provide: UsuarioLookupService,
          useValue: {
            resolverNome: vi.fn(() => 'Usuário'),
          },
        },
        {
          provide: NotificationService,
          useValue: mockNotifications,
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SolicitacoesTecnicasKanbanComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('deve inicializar e carregar as 4 macro-colunas operacionais do Kanban', () => {
    expect(component).toBeTruthy();
    expect(obterKanban).toHaveBeenCalledOnce();
  });

  it('deve mapear métricas e somar o total geral corretamente', () => {
    expect(component['totalGeral']()).toBe(27);
    const colunas = component['colunas']();
    expect(colunas.length).toBe(4);
    expect(colunas[0].label).toBe('Planejamento');
    expect(colunas[0].subGroups?.length).toBe(2);
    expect(colunas[1].label).toBe('Separação');
    expect(colunas[1].subGroups?.length).toBe(3);
    expect(colunas[2].label).toBe('Entrega & Campo');
    expect(colunas[2].subGroups?.length).toBe(3);
    expect(colunas[3].label).toBe('Recusado');
    expect(colunas[3].subGroups?.length).toBe(2);
  });

  it('deve colapsar sub-status que não contenham registros por padrão', () => {
    const mockComVazio: KanbanSolicitacaoTecnicaResponse = {
      colunas: [
        {
          etapa: 'PLANEJAMENTO',
          label: 'Planejamento',
          icone: 'pi pi-calendar',
          subColunas: [
            {
              subEtapa: 'PENDENTE_PLANEJAMENTO',
              label: 'Pendente de Planejamento',
              corPadrao: 'amber',
              itens: [],
              total: 0,
            },
            {
              subEtapa: 'PLANEJADO',
              label: 'Planejado',
              corPadrao: 'sky',
              itens: [],
              total: 5,
            },
          ],
          metricas: { total: 5, atrasadas: 0, foraPrazo: 0, diasMaisAntiga: null },
        },
      ],
    };

    obterKanban.mockReturnValueOnce(of(mockComVazio));
    component.carregarKanban();
    const cols = component['colunas']();
    const subVazio = cols[0].subGroups?.find((s) => s.id === 'PENDENTE_PLANEJAMENTO');
    const subComCards = cols[0].subGroups?.find((s) => s.id === 'PLANEJADO');

    expect(subVazio?.collapsed).toBe(true);
    expect(subComCards?.collapsed).toBe(false);
  });

  it('deve abrir o diálogo oficial de planejamento in-place ao arrastar para Planejado', () => {
    const item = criarItemMock({ statusSeparacao: null });
    component['onItemMoved']({
      item,
      fromColumnId: 'PLANEJAMENTO',
      fromSubGroupId: 'PENDENTE_PLANEJAMENTO',
      toColumnId: 'PLANEJAMENTO',
      toSubGroupId: 'PLANEJADO',
    });

    expect(buscarPorId).toHaveBeenCalledWith(item.id);
    expect(component['planningVisible']()).toBe(true);
    expect(component['planningDetail']()).toBeTruthy();
  });

  it('deve executar pular separação ao arrastar de Pronto para Separação para Separado', () => {
    const item = criarItemMock({
      statusSeparacao: 'PRONTO_PARA_SEPARACAO' as any,
      acoesPermitidas: ['PULAR_SEPARACAO'],
    });

    component['onItemMoved']({
      item,
      fromColumnId: 'SEPARACAO',
      fromSubGroupId: 'PRONTO_PARA_SEPARACAO',
      toColumnId: 'SEPARACAO',
      toSubGroupId: 'SEPARADO',
    });

    expect(pularSeparacao).toHaveBeenCalledWith(item.id, { versaoEsperada: 1 });
    expect(mockNotifications.success).toHaveBeenCalledWith(
      expect.stringContaining('pulada com sucesso')
    );
  });

  it('deve executar iniciar separação ao arrastar para Em Separação', () => {
    const item = criarItemMock({
      statusSeparacao: 'PRONTO_PARA_SEPARACAO' as any,
      acoesPermitidas: ['INICIAR_SEPARACAO'],
    });

    component['onItemMoved']({
      item,
      fromColumnId: 'SEPARACAO',
      fromSubGroupId: 'PRONTO_PARA_SEPARACAO',
      toColumnId: 'SEPARACAO',
      toSubGroupId: 'EM_SEPARACAO',
    });

    expect(iniciarSeparacao).toHaveBeenCalledWith(item.id, { versaoEsperada: 1 });
    expect(mockNotifications.success).toHaveBeenCalledWith(
      expect.stringContaining('iniciada com sucesso')
    );
  });

  it('deve executar concluir separação ao arrastar de Em Separação para Separado', () => {
    const item = criarItemMock({
      statusSeparacao: 'EM_SEPARACAO' as any,
      acoesPermitidas: ['CONCLUIR_SEPARACAO'],
    });

    component['onItemMoved']({
      item,
      fromColumnId: 'SEPARACAO',
      fromSubGroupId: 'EM_SEPARACAO',
      toColumnId: 'SEPARACAO',
      toSubGroupId: 'SEPARADO',
    });

    expect(concluirSeparacao).toHaveBeenCalledWith(item.id, { versaoEsperada: 1 });
    expect(mockNotifications.success).toHaveBeenCalledWith(
      expect.stringContaining('concluída com sucesso')
    );
  });

  it('deve abrir modal oficial de entrega ao arrastar para Entrega & Campo', () => {
    const item = criarItemMock({
      statusSeparacao: 'SEPARADO' as any,
      acoesPermitidas: ['ENTREGAR'],
    });

    component['onItemMoved']({
      item,
      fromColumnId: 'SEPARACAO',
      fromSubGroupId: 'SEPARADO',
      toColumnId: 'ENTREGA',
      toSubGroupId: 'PENDENTE_RETIRADA',
    });

    expect(buscarPorId).toHaveBeenCalledWith(item.id);
    expect(component['actionVisible']()).toBe(true);
    expect(component['actionMode']()).toBe('deliver');
  });

  it('deve abrir modal oficial de rejeição ao arrastar para Recusado', () => {
    const item = criarItemMock({
      acoesPermitidas: ['REJEITAR'],
    });

    component['onItemMoved']({
      item,
      fromColumnId: 'PLANEJAMENTO',
      fromSubGroupId: 'PLANEJADO',
      toColumnId: 'RECUSADO',
      toSubGroupId: 'REJEITADO',
    });

    expect(listarMotivosRejeicaoAtivos).toHaveBeenCalled();
    expect(component['rejectModalVisible']()).toBe(true);
    expect(component['rejectItem']()).toBe(item);
  });

  it('deve disparar diálogos in-place a partir de onCardAction', () => {
    const item = criarItemMock();

    // Planejar
    component['onCardAction']({ item, action: 'PLANEJAR' });
    expect(buscarPorId).toHaveBeenCalledWith(item.id);
    expect(component['planningVisible']()).toBe(true);

    // Entregar
    component['onCardAction']({ item, action: 'ENTREGAR' });
    expect(component['actionVisible']()).toBe(true);
    expect(component['actionMode']()).toBe('deliver');

    // Rejeitar
    component['onCardAction']({ item, action: 'REJEITAR' });
    expect(component['rejectModalVisible']()).toBe(true);
  });
});
