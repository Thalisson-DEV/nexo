import '@angular/compiler';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { of } from 'rxjs';
import { NotificationService } from '@ces/ui';
import { UsuarioLookupService } from '../../../../core/services/usuario-lookup.service';
import { RhService } from '../../../../core/services/rh.service';
import { ObrasService } from '../../../../core/services/obras.service';
import { SolicitacaoMaterialTecnicaService } from '../../../../core/services/solicitacao-material-tecnica.service';
import {
  HistoricoSolicitacaoEvento,
  HistoricoSolicitacaoResponse,
} from '../../../../core/models/solicitacao-material-tecnica.models';
import { SolicitacaoTecnicaHistoricoComponent } from './solicitacao-tecnica-historico.component';

try {
  TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
} catch {}

describe('SolicitacaoTecnicaHistoricoComponent', () => {
  let component: SolicitacaoTecnicaHistoricoComponent;
  let fixture: ComponentFixture<SolicitacaoTecnicaHistoricoComponent>;
  let serviceMock: { historico: ReturnType<typeof vi.fn> };
  let notificationMock: { error: ReturnType<typeof vi.fn> };
  let userLookupMock: { resolverNome: ReturnType<typeof vi.fn> };

  const makeEvent = (
    sequence: number,
    type: string,
    occurredAt: string,
    payload: HistoricoSolicitacaoEvento['payload'],
    extra: Partial<HistoricoSolicitacaoEvento> = {},
  ): HistoricoSolicitacaoEvento => ({
    id: `evt-${sequence}`,
    escopo: 'SOLICITACAO',
    idSolicitacao: 'sol-123',
    sequencia: sequence,
    tipo: type,
    origem: 'UI',
    autor: { id: 'usr-1', nome: 'João Almoxarife' },
    versao: sequence,
    ocorridoEm: occurredAt,
    payload,
    ...extra,
  });

  const mockHistoricoResponse: HistoricoSolicitacaoResponse = {
    page: { number: 0, size: 10, totalElements: 4, totalPages: 1 },
    integridade: [{ escopo: 'SOLICITACAO', idCadeia: 'cad-1', status: 'INTEGRO' }],
    eventos: [
      makeEvent(1, 'SOLICITACAO_CRIADA', '2026-08-28T09:00:00Z', {
        comando: 'CRIAR_SOLICITACAO',
        alteracoes: [],
        snapshots: {
          itens: [
            {
              id: 'item-1',
              material: { id: 'mat-1', snapshot: 'MAT-001 - Cabo Multiplexado 1KV' },
              quantidade: '10.000',
              tipoTecnico: 'ADICAO_COMUM',
            },
          ],
          dataPrevista: '2026-09-01',
          observacao: 'Observação inicial',
        },
      }),
      makeEvent(2, 'DATA_PREVISTA_ALTERADA', '2026-08-28T10:00:00Z', {
        comando: 'ATUALIZAR_PLANEJAMENTO',
        alteracoes: [
          {
            caminho: 'itens[item-1].quantidade',
            valorAnterior: '10.000',
            valorNovo: '15.500',
          },
          {
            caminho: 'itens[item-2].material',
            valorAnterior: { id: 'mat-001', snapshot: 'MAT-001 - Material anterior' },
            valorNovo: { id: 'mat-002', snapshot: 'MAT-002 - Poste de Concreto 9m' },
          },
          {
            caminho: 'dataPrevista',
            valorAnterior: '2026-09-01',
            valorNovo: '2026-09-05',
          },
        ],
        snapshots: {},
      }, { justificativa: 'Ajuste de quantidade, material e data.' }),
      makeEvent(3, 'SEPARACAO_CONCLUIDA', '2026-08-28T11:00:00Z', {
        comando: 'CONCLUIR_SEPARACAO',
        alteracoes: [],
        snapshots: {},
      }),
      makeEvent(4, 'PLANEJAMENTO_ATUALIZADO', '2026-08-28T12:00:00Z', {
        comando: 'ATUALIZAR_PLANEJAMENTO',
        alteracoes: [
          {
            caminho: 'itens[item-1]',
            valorAnterior: { id: 'item-1', quantidade: '3.000' },
            valorNovo: null,
          },
          {
            caminho: 'itens[item-2]',
            valorAnterior: null,
            valorNovo: { id: 'item-2', quantidade: '2.000' },
          },
        ],
        snapshots: {},
      }),
    ],
  };

  beforeEach(async () => {
    serviceMock = { historico: vi.fn().mockReturnValue(of(mockHistoricoResponse)) };
    notificationMock = { error: vi.fn() };
    userLookupMock = {
      resolverNome: vi.fn().mockImplementation((autor) => autor?.nome || 'Sistema'),
    };

    await TestBed.configureTestingModule({
      imports: [SolicitacaoTecnicaHistoricoComponent],
      providers: [
        { provide: SolicitacaoMaterialTecnicaService, useValue: serviceMock },
        { provide: NotificationService, useValue: notificationMock },
        { provide: UsuarioLookupService, useValue: userLookupMock },
        { provide: RhService, useValue: { listarEquipes: vi.fn().mockReturnValue(of({ content: [] })) } },
        { provide: ObrasService, useValue: { buscarObraPorId: vi.fn().mockReturnValue(of(null)) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SolicitacaoTecnicaHistoricoComponent);
    component = fixture.componentInstance;
    component.idSolicitacao = 'sol-123';
    fixture.detectChanges();
  });

  it('carrega e renderiza os eventos, usando tipo apenas para a categoria', () => {
    expect(serviceMock.historico).toHaveBeenCalledWith('sol-123', 0);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Linha do tempo');
    expect(compiled.textContent).toContain('Solicitação criada');
    expect(compiled.textContent).toContain('Planejamento atualizado');
    expect(compiled.textContent).toContain('Separação concluída');
  });

  it('exibe o estado inicial e permite expandir a lista de materiais ao clicar', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Detalhes registrados:');
    expect(compiled.textContent).toContain('01/09/2026');
    expect(compiled.textContent).toContain('Observação inicial');
    expect(compiled.textContent).toContain('Materiais');
    expect(compiled.textContent).toContain('(1 item)');

    // Inicialmente a lista de materiais fica recolhida
    const toggleButtons = compiled.querySelectorAll('.materials-toggle-btn');
    const firstToggle = toggleButtons[0] as HTMLButtonElement | null;
    expect(firstToggle).not.toBeNull();
    expect(compiled.querySelector('.materials-table')).toBeNull();

    // Clica para expandir os materiais do primeiro registro
    firstToggle?.click();
    fixture.detectChanges();

    // A tabela de materiais é renderizada com código, descrição e quantidade
    expect(compiled.querySelector('.materials-table')).not.toBeNull();
    expect(compiled.textContent).toContain('MAT-001');
    expect(compiled.textContent).toContain('Cabo Multiplexado 1KV');
    expect(compiled.textContent).toContain('10,000');

    // Clica novamente para recolher
    firstToggle?.click();
    fixture.detectChanges();
    expect(compiled.querySelector('.materials-table')).toBeNull();
  });

  it('mostra apenas o que mudou a partir do primeiro registro e coloca alterações de material no dropdown', () => {
    const compiled = fixture.nativeElement as HTMLElement;

    // Apenas o primeiro registro (criação) exibe "Detalhes registrados:"
    const titles = Array.from(compiled.querySelectorAll('.changes-title')).map((el) => el.textContent?.trim());
    const snapshotTitles = titles.filter((t) => t === 'Detalhes registrados:');
    expect(snapshotTitles.length).toBe(1);

    // Alteração geral (data prevista) é exibida em "O que mudou:"
    expect(compiled.textContent).toContain('Data prevista');
    expect(compiled.textContent).toContain('01/09/2026');
    expect(compiled.textContent).toContain('05/09/2026');

    // Dropdowns de materiais:
    // Evento 1 (Criação): 1 item inicial
    // Evento 2 (Modificação de materiais): 2 itens modificados
    // Evento 3 (Separação concluída - sem mudanças de material): DROPDOWN OCULTO
    // Evento 4 (Modificação de materiais): 2 itens modificados
    const toggleButtons = compiled.querySelectorAll('.materials-toggle-btn');
    expect(toggleButtons.length).toBe(3);

    // Clica no dropdown de modificações do Evento 2 (segundo botão)
    (toggleButtons[1] as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(compiled.textContent).toContain('MAT-001');
    expect(compiled.textContent).toContain('10,000');
    expect(compiled.textContent).toContain('15,500');
    expect(compiled.textContent).toContain('MAT-001 - Material anterior');
    expect(compiled.textContent).toContain('MAT-002 - Poste de Concreto 9m');
  });

  it('apresenta inclusão e remoção de materiais dentro do dropdown de materiais', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const toggleButtons = compiled.querySelectorAll('.materials-toggle-btn');

    // Evento 4 é o terceiro botão de materiais
    (toggleButtons[2] as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(compiled.textContent).toContain('Item removido');
    expect(compiled.textContent).toContain('3,000');
    expect(compiled.textContent).toContain('Item incluído');
    expect(compiled.textContent).toContain('2,000');
  });

  it('ordena a linha do tempo pela sequência, mesmo com array e datas fora de ordem', () => {
    const current = component['response']();
    if (!current) throw new Error('Resposta de histórico não carregada');

    component['response'].set({
      ...current,
      eventos: current.eventos
        .map((event) => ({
          ...event,
          ocorridoEm:
            event.sequencia === 1
              ? '2026-08-28T12:00:00Z'
              : event.sequencia === 2
                ? '2026-08-28T09:00:00Z'
                : '2026-08-28T08:00:00Z',
        }))
        .reverse(),
    });
    fixture.detectChanges();

    expect(component['sortedEvents']().map((event) => event.sequencia)).toEqual([1, 2, 3, 4]);
  });

  it('aceita os nomes legados anterior/novo e material legado como UUID', () => {
    const diff = {
      caminho: 'itens[item-1].quantidade',
      anterior: '3.000',
      novo: null,
    };

    expect(component['getOldValue'](diff)).toBe('3.000');
    expect(component['getNewValue'](diff)).toBeNull();
    expect(component['oldDisplayValue'](diff)).toBe('3,000');
    expect(component['newDisplayValue'](diff)).toBe('removido');
    expect(component['itemId'](diff.caminho)).toBe('item-1');
    expect(component['formatValue']('material-uuid', 'itens[item-1].material')).toBe('material-uuid');
  });

  it('resolve o material do item para identificar diffs operacionais', () => {
    const event = makeEvent(5, 'ENTREGA_CONFIRMADA', '2026-08-28T13:00:00Z', {
      comando: 'CONFIRMAR_ENTREGA',
      alteracoes: [
        {
          caminho: 'itens[item-1].quantidadeEntregaLiquida',
          valorAnterior: '0.000',
          valorNovo: '2.000',
        },
      ],
      snapshots: {
        itens: [
          {
            id: 'item-1',
            material: { id: 'mat-1', snapshot: 'MAT-001 - Cabo Multiplexado 1KV' },
          },
        ],
      },
    });

    expect(component['itemDescription'](event, 'itens[item-1].quantidadeEntregaLiquida'))
      .toBe('MAT-001 - Cabo Multiplexado 1KV');
    expect(component['fieldLabel']('itens[item-1].quantidadeEntregaLiquida'))
      .toBe('Quantidade entregue');
    expect(component['formatValue']('2.000', 'itens[item-1].quantidadeEntregaLiquida'))
      .toBe('2,000');
  });

  it('humaniza referências de Obra, Equipe, Tipos e Almoxarifado sem vazar IDs técnicos', () => {
    component['equipesMap'].set(new Map([['b7ef1a42-bc9d-4660-a618-a9f1ca01fbda', 'EQ-01 — Linha Viva']]));
    component['obrasMap'].set(new Map([['35a8157b-dcd3-4898-bd30-55a2e7cb833f', 'OB-1042 — Expansão']]));

    expect(component['formatValue']('DEVOLUCAO_COMUM', 'tipoTecnico')).toBe('Devolução comum (222)');
    expect(component['formatValue']('ADICAO_COMUM', 'tipoTecnico')).toBe('Adição comum (221)');
    expect(component['formatValue']('OBRA', 'tipoObra')).toBe('Obra');
    expect(component['formatValue']('CORRETIVA', 'tipoObra')).toBe('Corretiva');
    expect(component['formatValue']('ALMOXARIFADO_JUAZEIRO', 'almoxarifado')).toBe('Almoxarifado Juazeiro');

    // Equipe por UUID resolvido no mapa
    expect(component['formatValue']('b7ef1a42-bc9d-4660-a618-a9f1ca01fbda', 'equipe')).toBe('EQ-01 — Linha Viva');

    // Obra por objeto com snapshot ou resolvido no mapa
    expect(component['formatValue']({ id: '35a8157b-dcd3-4898-bd30-55a2e7cb833f' }, 'obra')).toBe('OB-1042 — Expansão');
    expect(component['formatValue']({ id: '35a8157b-dcd3-4898-bd30-55a2e7cb833f', snapshot: 'OB-1042 — Expansão Juazeiro' }, 'obra')).toBe('OB-1042 — Expansão Juazeiro');

    // Fallback gracioso para UUID não encontrado (sem vazar JSON bruto {"id": "..."})
    expect(component['formatValue']({ id: '99999999-0000-0000-0000-000000000000' }, 'obra')).toBe('Obra (99999999)');
    expect(component['formatValue']({ id: '88888888-0000-0000-0000-000000000000' }, 'equipe')).toBe('Equipe (88888888)');
  });

  it('omite campos de Obra quando ocultarObra estiver habilitado', () => {
    component.ocultarObra = true;
    const event = {
      id: 'evt-1',
      tipo: 'SOLICITADO',
      ocorridoEm: '2026-09-15T10:00:00Z',
      autor: 'Sistema',
      payload: {
        snapshots: {
          obra: 'OB-100',
          tipoObra: 'OBRA',
          almoxarifado: 'Almox Central',
          dataPrevista: null,
        },
        alteracoes: [
          { caminho: 'obra', valorAnterior: null, valorNovo: 'OB-100' },
          { caminho: 'almoxarifado', valorAnterior: null, valorNovo: 'Almox Central' },
        ],
      },
    } as any;

    const snapshots = component['extractSnapshots'](event);
    expect(snapshots).toEqual([{ rotulo: 'Almoxarifado', valor: 'Almox Central' }]);

    const diffs = component['alterations'](event);
    expect(diffs).toEqual([{ caminho: 'almoxarifado', valorAnterior: null, valorNovo: 'Almox Central' }]);
  });

  it('omite IDs técnicos e campos imutáveis de snapshots e alterações', () => {
    const event = {
      id: 'evt-2',
      tipo: 'PLANEJAMENTO_ATUALIZADO',
      ocorridoEm: '2026-09-15T11:00:00Z',
      autor: 'Sistema',
      payload: {
        snapshots: {
          id: '11111111-1111-1111-1111-111111111111',
          idSolicitacao: '22222222-2222-2222-2222-222222222222',
          idOperacao: '33333333-3333-3333-3333-333333333333',
          idOperacaoOriginal: '44444444-4444-4444-4444-444444444444',
          versao: 3,
          contexto: 'TECNICO',
          dataSolicitacao: '2026-09-15T08:00:00Z',
          tipoTecnico: 'ADICAO_COMUM',
          tipoObra: 'OBRA',
          almoxarifado: 'Almox Central',
          dataPrevista: '2026-09-20',
          observacao: 'Nova observação',
        },
        alteracoes: [
          { caminho: 'id', valorAnterior: null, valorNovo: '1111' },
          { caminho: 'versao', valorAnterior: 2, valorNovo: 3 },
          { caminho: 'idOperacao', valorAnterior: null, valorNovo: '3333' },
          { caminho: 'contexto', valorAnterior: 'COMERCIAL', valorNovo: 'TECNICO' },
          { caminho: 'dataPrevista', valorAnterior: '2026-09-15', valorNovo: '2026-09-20' },
          { caminho: 'observacao', valorAnterior: 'Obs antiga', valorNovo: 'Nova observação' },
        ],
      },
    } as any;

    const snapshots = component['extractSnapshots'](event);
    // Deve conter apenas os campos válidos e não-imutáveis
    expect(snapshots).toEqual([
      { rotulo: 'Almoxarifado', valor: 'Almox Central' },
      { rotulo: 'Data prevista', valor: '20/09/2026' },
      { rotulo: 'Observação', valor: 'Nova observação' },
    ]);

    const diffs = component['alterations'](event);
    // Deve conter apenas as alterações de planejamento válidas (excluindo id, versao, idOperacao, contexto)
    expect(diffs).toEqual([
      { caminho: 'dataPrevista', valorAnterior: '2026-09-15', valorNovo: '2026-09-20' },
      { caminho: 'observacao', valorAnterior: 'Obs antiga', valorNovo: 'Nova observação' },
    ]);
  });

  it('separa corretamente alterações gerais de alterações de material', () => {
    const event = {
      id: 'evt-test',
      sequencia: 2,
      tipo: 'SOLICITACAO_ALTERADA',
      payload: {
        alteracoes: [
          { caminho: 'dataPrevista', valorAnterior: '2026-09-01', valorNovo: '2026-09-05' },
          { caminho: 'status', valorAnterior: 'PENDENTE', valorNovo: 'APROVADO' },
          { caminho: 'itens[item-1].quantidade', valorAnterior: '5.000', valorNovo: '10.000' },
          { caminho: 'itens[item-2]', valorAnterior: null, valorNovo: { id: 'item-2', quantidade: '1.000' } },
        ],
      },
    } as any;

    expect(component['isInitialEvent'](event)).toBe(false);

    const general = component['generalAlterations'](event);
    expect(general.map((d) => d.caminho)).toEqual(['dataPrevista', 'status']);

    const material = component['materialAlterations'](event);
    expect(material.map((d) => d.caminho)).toEqual(['itens[item-1].quantidade', 'itens[item-2]']);

    const mods = component['extractMaterialModifications'](event);
    expect(mods.length).toBe(2);
    expect(mods[0].itemId).toBe('item-1');
    expect(mods[0].mudancas[0].campo).toBe('Quantidade');
    expect(mods[0].mudancas[0].anterior).toBe('5,000');
    expect(mods[0].mudancas[0].novo).toBe('10,000');

    expect(mods[1].itemId).toBe('item-2');
    expect(mods[1].acaoPrincipal).toBe('inclusao');
  });

  it('oculta o dropdown de materiais quando um evento não possui alterações de material', () => {
    const eventSemMaterial = {
      id: 'evt-sem-mat',
      sequencia: 3,
      tipo: 'STATUS_ALTERADO',
      payload: {
        alteracoes: [
          { caminho: 'status', valorAnterior: 'PENDENTE', valorNovo: 'APROVADO' },
        ],
        snapshots: {
          itens: [
            { id: 'item-1', material: { snapshot: 'MAT-001' }, quantidade: '10.000' },
          ],
        },
      },
    } as any;

    // Mesmo que snapshots contenha itens, não é evento inicial, logo extractMaterialModifications é usado
    expect(component['isInitialEvent'](eventSemMaterial)).toBe(false);
    expect(component['materialAlterations'](eventSemMaterial)).toEqual([]);
    expect(component['extractMaterialModifications'](eventSemMaterial)).toEqual([]);
  });
});
