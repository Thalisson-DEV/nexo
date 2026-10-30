import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { CesConfirmationService, NotificationService } from '@ces/ui';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { LookupStore } from '../../../core/lookup/store/lookup.store';
import { MovimentacaoImportRow, MovimentacaoOperacaoSummaryResponse } from '../../../core/models/almoxarifado.models';
import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { RhService } from '../../../core/services/rh.service';
import { abrirListagem, chipsDoPainel, paginaTeste } from '../../../shared/listagem/listagem-teste';
import { MovimentacoesEstoqueComponent } from './movimentacoes-estoque.component';
import { MovimentacoesImportacaoComponent, extrairSeriais } from './movimentacoes-importacao.component';

/* eslint-disable @typescript-eslint/no-explicit-any */
const operacao = {
  idReferenciaOperacao: 'op123456-xyz',
  tipo: 'TRANSFERENCIA',
  dataHora: '2026-09-10T10:00:00Z',
  operadorNome: 'Carlos',
  estoqueOrigem: { id: 'e1', nomeIdentificador: 'ALMOX CENTRAL' },
  estoqueDestino: { id: 'e2', nomeIdentificador: 'CAMPO EQ-01' }
} as unknown as MovimentacaoOperacaoSummaryResponse;

describe('MovimentacoesEstoqueComponent', () => {
  let almox: Record<string, ReturnType<typeof vi.fn>>;
  const providers = () => [
    { provide: AlmoxarifadoService, useValue: almox },
    { provide: RhService, useValue: { listarBasesOperacionais: () => paginaTeste([]) } },
    { provide: LookupStore, useValue: { searchMaterials: () => of({ items: [], nextCursor: null, hasMore: false }) } }
  ];
  const abrir = (url = '/lista') => abrirListagem(MovimentacoesEstoqueComponent, 'lista', url, providers());
  const ultimaConsulta = () => almox['listarMovimentacoes'].mock.lastCall![0];

  beforeEach(() => {
    TestBed.resetTestingModule();
    almox = {
      listarMovimentacoes: vi.fn(() => paginaTeste([operacao])),
      listarEstoquesAtivos: vi.fn(() => paginaTeste([{ id: 'e1', nomeIdentificador: 'ALMOX CENTRAL', ativo: true }])),
      listarMateriais: vi.fn(() => paginaTeste([])),
      buscarOperacaoPorIdReferencia: vi.fn(() =>
        of({ ...operacao, itens: [{ id: 'i1', quantidade: 2, material: { codigo: 'M-1', descricao: 'Cabo' } }, { id: 'i2', quantidade: 3.5, material: { codigo: 'M-2', descricao: 'Poste' } }] })
      )
    };
  });

  afterEach(() => localStorage.clear());

  it('abre já nos dados, mais recentes primeiro, com tipo e estoques', async () => {
    const { el } = await abrir();
    expect(ultimaConsulta()).toEqual({ page: 0, size: 25, sort: 'dataHora,desc' });
    expect(el.textContent).toContain('#op123456');
    expect(el.textContent).toContain('Transferência');
    expect(el.textContent).toContain('CAMPO EQ-01');
  });

  it('filtros da URL viram a consulta com chips nomeados; período em ISO', async () => {
    const { el } = await abrir('/lista?estoque=e1&tipo=TRANSFERENCIA&periodo=2026-09-01~&busca=cabo');
    const consulta = ultimaConsulta();
    expect(consulta).toMatchObject({ idEstoque: 'e1', tipo: 'TRANSFERENCIA', buscaGlobal: 'cabo' });
    expect(new Date(consulta.dataInicio).getDate()).toBe(1);
    expect(chipsDoPainel(el)).toEqual(expect.arrayContaining(['Estoque: ALMOX CENTRAL', 'Tipo: Transferência']));
  });

  it('prévia carrega os itens e soma o total; nova operação navega ou abre a importação', async () => {
    const { componente } = await abrir();
    const tela = componente as unknown as {
      verDetalhes(m: unknown): void;
      totalSelecionado(): number;
      navigateToOperation(r: string): void;
      importacaoAberta(): boolean;
    };
    tela.verDetalhes(operacao);
    expect(tela.totalSelecionado()).toBe(5.5);
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    tela.navigateToOperation('/almoxarifado/entrada-avulsa');
    expect(navegar).toHaveBeenCalledWith(['/almoxarifado/entrada-avulsa']);
    tela.navigateToOperation('IMPORT_MODAL');
    expect(tela.importacaoAberta()).toBe(true);
  });
});

describe('MovimentacoesImportacaoComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  let tela: any;
  const linha = (extra: Partial<MovimentacaoImportRow> = {}): MovimentacaoImportRow =>
    ({ linha: 2, tipo: 'TRANSFERENCIA', nomeEstoqueOrigem: 'A', nomeEstoqueDestino: 'B', codigoMaterial: 'M-1', quantidade: 1, condicao: 'NOVO', status: 'AGUARDANDO_PROCESSAMENTO', ...extra }) as MovimentacaoImportRow;

  beforeEach(() => {
    TestBed.resetTestingModule();
    servico = { confirmarImportacaoMovimentacoes: vi.fn(), validarImportacaoMovimentacoes: vi.fn(), baixarModeloCsvMovimentacoes: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        { provide: AlmoxarifadoService, useValue: servico },
        { provide: NotificationService, useValue: { error: vi.fn(), warning: vi.fn(), success: vi.fn(), info: vi.fn() } },
        { provide: CesConfirmationService, useValue: { confirm: () => Promise.resolve(true) } }
      ]
    });
    const fixture = TestBed.createComponent(MovimentacoesImportacaoComponent);
    fixture.componentRef.setInput('estoques', [
      { id: 'a', nomeIdentificador: 'A', ativo: true },
      { id: 'b', nomeIdentificador: 'B', ativo: true }
    ]);
    fixture.componentRef.setInput('materiais', [
      { id: 'm1', codigo: 'M-1', descricao: 'Cabo', detalhaSerie: false },
      { id: 'm2', codigo: 'M-2', descricao: 'Medidor', detalhaSerie: true }
    ]);
    tela = fixture.componentInstance;
  });

  it('separa números de série por vírgula, barra ou espaço', () => {
    expect(extrairSeriais('SN-1, SN-2/SN-3')).toEqual(['SN-1', 'SN-2', 'SN-3']);
    expect(extrairSeriais('SN-1 SN-2')).toEqual(['SN-1', 'SN-2']);
    expect(extrairSeriais('  ')).toEqual([]);
  });

  it('material serializado: quantidade acompanha as séries; duplicadas e mesmo estoque viram erro', () => {
    const serializado = linha({ codigoMaterial: 'M-2', numSerie: 'S1, S2, S3', quantidade: 1, editing: true });
    tela.finishImportEdit(serializado);
    expect(serializado).toMatchObject({ quantidade: 3, status: 'AGUARDANDO_PROCESSAMENTO', idMaterial: 'm2', idEstoqueOrigem: 'a', idEstoqueDestino: 'b' });

    const duplicada = linha({ codigoMaterial: 'M-2', numSerie: 'S1, s1', editing: true });
    tela.finishImportEdit(duplicada);
    expect(duplicada.mensagemErro).toContain('duplicados');

    const mesmo = linha({ nomeEstoqueDestino: 'A', editing: true });
    tela.finishImportEdit(mesmo);
    expect(mesmo.campoErro).toBe('destino');
  });

  it('processa só as válidas e, no parcial, mantém só as com erro', async () => {
    tela.registros.set([linha({ editing: false }), linha({ linha: 3, status: 'COM_ERRO', mensagemErro: 'x' })]);
    servico['confirmarImportacaoMovimentacoes'].mockReturnValue(
      of({ registros: [linha({ linha: 3, codigoMaterial: 'M-9', status: 'COM_ERRO' })], totalProcessados: 2, totalSucesso: 1, totalFalha: 1, processamento: { estado: 'PARCIAL' } })
    );
    await tela.onProcessImport();
    const enviados = servico['confirmarImportacaoMovimentacoes'].mock.calls[0][0].registros;
    expect(enviados).toHaveLength(1);
    expect(enviados[0].editing).toBeUndefined();
    expect(tela.registros().map((r: MovimentacaoImportRow) => r.codigoMaterial)).toEqual(['M-9']);
  });
});
