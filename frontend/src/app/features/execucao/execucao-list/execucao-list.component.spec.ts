import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { LookupStore } from '../../../core/lookup/store/lookup.store';
import { ContratoService } from '../../../core/services/contrato.service';
import { ExecucaoService } from '../../../core/services/execucao.service';
import { abrirListagem, chipsDoPainel, paginaTeste } from '../../../shared/listagem/listagem-teste';
import { RecorteDashboardService } from '../dashboards/recorte-dashboard.service';
import { ExecucaoListComponent } from './execucao-list.component';

const execucao = {
  id: 'e1',
  referenciaNota: '123456',
  idTurno: 't1',
  tipoServico: { id: 'ts-cs', codigo: 'CS', descricao: 'CORTE', ativo: true },
  equipe: { id: 'q1', codigo: 'EQ-01' },
  municipio: { id: 'm1', nome: 'JUAZEIRO' },
  dataLancamento: '2026-10-10T10:00:00',
  statusExecucao: 'CONCLUIDO',
  valorTotalFaturado: 850,
  acoes: [],
  materiais: []
};

describe('ExecucaoListComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  let materiais: ReturnType<typeof vi.fn>;
  const abrir = (url = '/lista') =>
    abrirListagem(ExecucaoListComponent, 'lista', url, [
      { provide: ExecucaoService, useValue: servico },
      { provide: ContratoService, useValue: { listarAcoesAtivas: () => paginaTeste([]) } },
      { provide: LookupStore, useValue: { searchMaterials: materiais } },
      {
        provide: RecorteDashboardService,
        useValue: { carregarEquipes: vi.fn(), carregandoEquipes: signal(false), opcoesEquipes: signal([{ label: 'EQ-01', value: 'q1' }]) }
      }
    ]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    servico = {
      listarExecucoes: vi.fn(() => paginaTeste([execucao])),
      listarTiposServico: vi.fn(() => paginaTeste([{ id: 'ts-cs', codigo: 'CS', descricao: 'CORTE', ativo: true }]))
    };
    materiais = vi.fn(() => of({ items: [{ value: 'mat1', label: 'CABO 16MM' }], nextCursor: null, hasMore: false }));
  });

  afterEach(() => localStorage.clear());

  it('abre nos últimos 30 dias, mais recentes primeiro', async () => {
    const { el } = await abrir();
    const filtro = servico['listarExecucoes'].mock.calls[0][0];
    const esperado = new Date();
    esperado.setDate(esperado.getDate() - 30);
    expect(new Date(filtro.dataLancamentoDe).toDateString()).toBe(esperado.toDateString());
    expect(filtro).toMatchObject({ sort: ['dataLancamento,desc'], page: 0, size: 25 });
    expect(chipsDoPainel(el)[0]).toMatch(/^Lançamento: a partir de /);
    expect(el.textContent).toContain('123456');
    expect(el.textContent).toContain('CS · CORTE');
  });

  it('vindo do turno (?idTurno=) mostra todas as notas dele, sem limite de data', async () => {
    const { el } = await abrir('/lista?idTurno=t1');
    expect(servico['listarExecucoes']).toHaveBeenLastCalledWith(expect.objectContaining({ idTurno: 't1', dataLancamentoDe: undefined }));
    expect(chipsDoPainel(el)).toEqual(['Turno: notas do turno']);
  });

  it('o material escolhido vira filtro e chip com o nome', async () => {
    const { componente, el, harness } = await abrir('/lista?lancamento=~');
    const tela = componente as unknown as { materiais: { search(q: string): void }; escolherMaterial(id: string): void; lista: { apply(): void } };
    tela.materiais.search('cabo');
    await new Promise((r) => setTimeout(r, 400));
    tela.escolherMaterial('mat1');
    tela.lista.apply();
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(servico['listarExecucoes']).toHaveBeenLastCalledWith(expect.objectContaining({ idMaterial: 'mat1' }));
    expect(chipsDoPainel(el)).toContain('Material: CABO 16MM');
  });

  it('filtra por tipo de serviço, inclusive pelas notas sem tipo', async () => {
    const { el } = await abrir('/lista?tipo=ts-cs');
    expect(servico['listarExecucoes']).toHaveBeenLastCalledWith(expect.objectContaining({ idTipoServico: 'ts-cs', semTipoServico: undefined }));
    expect(chipsDoPainel(el)).toContain('Tipo de serviço: CS · CORTE');

    TestBed.resetTestingModule();
    await abrir('/lista?tipo=sem-tipo');
    expect(servico['listarExecucoes']).toHaveBeenLastCalledWith(expect.objectContaining({ idTipoServico: undefined, semTipoServico: true }));
  });

  it('filtra pelo tipo de lançamento e pelas avulsas sem número oficial', async () => {
    const { el } = await abrir('/lista?tipoNota=OCORRENCIA&semNumero=true');
    expect(servico['listarExecucoes']).toHaveBeenLastCalledWith(expect.objectContaining({ tipoNota: 'OCORRENCIA', aguardandoNumeroOficial: true }));
    expect(chipsDoPainel(el)).toContain('Tipo de lançamento: Ocorrência');
    expect(chipsDoPainel(el)).toContain('Avulsas: sem número oficial');
  });

  it('marca a avulsa sem número oficial e oferece informar o número', async () => {
    servico['listarExecucoes'] = vi.fn(() =>
      paginaTeste([{ ...execucao, referenciaNota: 'AV-EQ01-261019-01', tipoNota: 'AVULSA', referenciaProvisoria: 'AV-EQ01-261019-01', aguardandoNumeroOficial: true }])
    );
    const { el } = await abrir();
    expect(el.textContent).toContain('Sem nº oficial');
    expect(el.querySelector('[aria-label="Informar número oficial da nota AV-EQ01-261019-01"]')).not.toBeNull();
  });

  it('ver nota abre o detalhe em nova aba', async () => {
    const abrirAba = vi.fn();
    vi.stubGlobal('open', abrirAba);
    const { componente } = await abrir();
    (componente as unknown as { verNota(e: unknown): void }).verNota(execucao);
    expect(abrirAba).toHaveBeenCalledWith(TestBed.inject(Router).serializeUrl(TestBed.inject(Router).createUrlTree(['/execucao/comercial', 'e1'])), '_blank');
    vi.unstubAllGlobals();
  });
});
