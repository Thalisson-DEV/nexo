import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { LookupStore } from '../../../core/lookup/store/lookup.store';
import { MetaInspecaoResponse } from '../../../core/models/meta-inspecao.models';
import { MetasInspecaoService } from '../../../core/services/metas-inspecao.service';
import { RhService } from '../../../core/services/rh.service';
import { SstService } from '../../../core/services/sst.service';
import { abrirListagem, chipsDoPainel, paginaTeste } from '../../../shared/listagem/listagem-teste';
import { MetasInspecaoComponent } from './metas-inspecao.component';

const meta: MetaInspecaoResponse = {
  id: 'meta-1',
  idTipoInspecao: 'tipo-1',
  nomeTipoInspecao: 'Campo',
  escopo: 'COLABORADOR',
  idFuncao: null,
  nomeFuncao: null,
  idColaborador: 'colaborador-1',
  nomeColaborador: 'Maria da Silva',
  quantidade: 2,
  periodicidade: 'MENSAL',
  vigenciaInicio: '2026-09-01',
  vigenciaFim: null,
  descricao: null,
  ativo: true,
  janelaAtualInicio: '2026-09-01',
  janelaAtualFim: '2026-09-30'
};

describe('MetasInspecaoComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  let searchCollaborators: ReturnType<typeof vi.fn>;
  const abrir = (url = '/lista') =>
    abrirListagem(MetasInspecaoComponent, 'lista', url, [
      { provide: LookupStore, useValue: { searchCollaborators } },
      { provide: MetasInspecaoService, useValue: servico },
      { provide: RhService, useValue: { listarFuncoesAtivas: vi.fn(() => paginaTeste([{ id: 'f1', nome: 'ELETRICISTA' }])) } },
      { provide: SstService, useValue: { listarOpcoesTiposInspecao: vi.fn(() => of([{ id: 'tipo-1', nome: 'Campo' }])) } }
    ]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    searchCollaborators = vi.fn(() => of({ items: [{ value: 'colaborador-1', label: 'Maria da Silva' }], nextCursor: null, hasMore: false }));
    servico = {
      listarMetas: vi.fn(() => paginaTeste([meta])),
      criarMeta: vi.fn(() => of({})),
      atualizarMeta: vi.fn(() => of({})),
      ativarMeta: vi.fn(() => of({})),
      desativarMeta: vi.fn(() => of({}))
    };
  });

  afterEach(() => {
    vi.useRealTimers();
    localStorage.clear();
  });

  it('abre já nos dados, vigências mais recentes primeiro, com o alvo e a janela atual', async () => {
    const { el } = await abrir('/lista?tipo=tipo-1');
    expect(servico['listarMetas']).toHaveBeenLastCalledWith(expect.objectContaining({ idTipoInspecao: 'tipo-1', page: 0, size: 25, sort: 'vigenciaInicio,desc' }));
    expect(el.textContent).toContain('Maria da Silva');
    expect(el.textContent).toContain('01/09/2026 a 30/09/2026');
    expect(chipsDoPainel(el)).toEqual(['Tipo: Campo']);
  });

  it('não consulta colaboradores com menos de dois caracteres e normaliza a busca', async () => {
    const { componente } = await abrir();
    vi.useFakeTimers();
    const tela = componente as unknown as { searchColaboradores(q: string): void };
    tela.searchColaboradores('m');
    vi.advanceTimersByTime(300);
    expect(searchCollaborators).not.toHaveBeenCalled();
    tela.searchColaboradores('  MARIA ');
    vi.advanceTimersByTime(300);
    expect(searchCollaborators).toHaveBeenCalledWith({ query: 'maria', cursor: null });
  });

  it('a edição hidrata o formulário pelo DTO e trava tipo e alvo', async () => {
    const { componente } = await abrir();
    const tela = componente as unknown as {
      showEditDialog(m: MetaInspecaoResponse): void;
      metaForm: { getRawValue(): Record<string, unknown>; get(c: string): { disabled: boolean } | null };
      colaboradores(): unknown[];
    };
    tela.showEditDialog(meta);
    expect(tela.metaForm.getRawValue()['idColaborador']).toBe('colaborador-1');
    expect(tela.metaForm.get('escopo')!.disabled).toBe(true);
    expect(tela.colaboradores()).toContainEqual({ value: 'colaborador-1', label: 'Maria da Silva' });
  });

  it('422 de sobreposição oferece as metas do mesmo alvo', async () => {
    servico['atualizarMeta'] = vi.fn(() => throwError(() => ({ status: 422, error: { detail: 'Vigência sobrepõe outra meta ativa.' } })));
    const { componente } = await abrir();
    const tela = componente as unknown as {
      showEditDialog(m: MetaInspecaoResponse): void;
      onSave(): void;
      conflito(): string | null;
      filtrarPorAlvoEmConflito(): void;
      formularioAberto(): boolean;
    };
    tela.showEditDialog(meta);
    tela.onSave();
    expect(tela.conflito()).toContain('sobrepõe');
    tela.filtrarPorAlvoEmConflito();
    await new Promise((r) => setTimeout(r));
    expect(tela.formularioAberto()).toBe(false);
    expect(servico['listarMetas']).toHaveBeenLastCalledWith(expect.objectContaining({ escopo: 'COLABORADOR', idColaborador: 'colaborador-1', ativo: true }));
  });
});
