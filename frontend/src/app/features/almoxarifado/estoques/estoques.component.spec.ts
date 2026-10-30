import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { ImportacaoService } from '../../../core/services/importacao.service';
import { RhService } from '../../../core/services/rh.service';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../shared/listagem/listagem-teste';
import { EstoquesComponent } from './estoques.component';

const estoque = { id: 'e1', nomeIdentificador: 'ESTOQUE EQ-01', tipoEstoque: { id: 't1', nome: 'EQUIPE' }, idEquipe: 'q1', idBaseOperacional: 'b9', ativo: true };

describe('EstoquesComponent', () => {
  let almox: Record<string, ReturnType<typeof vi.fn>>;
  let rh: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = (url = '/lista') =>
    abrirListagem(EstoquesComponent, 'lista', url, [
      { provide: AlmoxarifadoService, useValue: almox },
      { provide: RhService, useValue: rh },
      { provide: ImportacaoService, useValue: { baixarModeloCsv: vi.fn() } }
    ]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    almox = {
      listarTiposEstoqueAtivos: vi.fn(() => paginaTeste([{ id: 't1', nome: 'EQUIPE' }])),
      listarEstoques: vi.fn(() => paginaTeste([estoque])),
      criarEstoque: vi.fn(() => of(estoque)),
      atualizarEstoque: vi.fn(() => of(estoque)),
      ativarEstoque: vi.fn(() => of(undefined)),
      desativarEstoque: vi.fn(() => of(undefined))
    };
    rh = {
      listarBasesOperacionaisAtivas: vi.fn(() => paginaTeste([{ id: 'b1', nome: 'JUAZEIRO' }])),
      listarEquipes: vi.fn(() => paginaTeste([{ id: 'q1', codigo: 'EQ-01' }])),
      buscarEquipePorId: vi.fn(() => of({ id: 'q1', codigo: 'EQ-01' })),
      buscarBaseOperacionalPorId: vi.fn(() => of({ id: 'b9', nome: 'BASE DESATIVADA' }))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre já nos dados e busca o código da equipe e o nome da base que não vieram na resposta', async () => {
    const { el, harness } = await abrir();
    harness.detectChanges();
    expect(almox['listarEstoques']).toHaveBeenCalledWith({ idTipoEstoque: undefined, ativo: undefined, buscaGlobal: undefined, page: 0, size: 25, sort: 'nomeIdentificador,asc' });
    expect(rh['buscarEquipePorId']).toHaveBeenCalledWith('q1');
    expect(rh['buscarBaseOperacionalPorId']).toHaveBeenCalledWith('b9');
    expect(el.textContent).toContain('EQ-01');
    expect(el.textContent).toContain('BASE DESATIVADA');
  });

  it('restaura o tipo de estoque da URL', async () => {
    const { el } = await abrir('/lista?tipo=t1');
    expect(almox['listarEstoques']).toHaveBeenLastCalledWith(expect.objectContaining({ idTipoEstoque: 't1' }));
    expect(chipsDoPainel(el)).toEqual(['Tipo: EQUIPE']);
  });

  it('"n" exige identificador, tipo e base', async () => {
    const { componente } = await abrir();
    tecla('n');
    const tela = componente as unknown as Record<string, { set(v: unknown): void }> & { salvar(): void };
    tela['identificador'].set('Almoxarifado Central');
    tela.salvar();
    expect(almox['criarEstoque']).not.toHaveBeenCalled();
    tela['idTipo'].set('t1');
    tela['idBase'].set('b1');
    tela.salvar();
    expect(almox['criarEstoque']).toHaveBeenCalledWith({ nomeIdentificador: 'Almoxarifado Central', idTipoEstoque: 't1', idBaseOperacional: 'b1', idEquipe: undefined });
  });
});
