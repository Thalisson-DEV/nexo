import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { LookupStore } from '../../../core/lookup/store/lookup.store';
import { CoordenadorService } from '../../../core/services/coordenador.service';
import { ImportacaoService } from '../../../core/services/importacao.service';
import { RhService } from '../../../core/services/rh.service';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../shared/listagem/listagem-teste';
import { CoordenadoresComponent } from './coordenadores.component';

const colaborador = { id: 'c1', nomeCompleto: 'ANA LIMA', cpf: '12345678901', baseOperacional: { id: 'b1', nome: 'JUAZEIRO' }, funcao: { id: 'f1', nome: 'COORDENADORA' } };
const coordenador = { id: 'k1', colaborador, dataInicio: '2025-01-01', dataFim: null, ativo: true };

describe('CoordenadoresComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = (url = '/lista') =>
    abrirListagem(CoordenadoresComponent, 'lista', url, [
      { provide: CoordenadorService, useValue: servico },
      { provide: RhService, useValue: { buscarColaboradorPorId: vi.fn(() => of(colaborador)) } },
      { provide: LookupStore, useValue: { searchCollaborators: vi.fn(() => of({ items: [], hasMore: false })) } },
      { provide: ImportacaoService, useValue: { baixarModeloCsv: vi.fn() } }
    ]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    servico = {
      listar: vi.fn(() => paginaTeste([coordenador])),
      criar: vi.fn(() => of(coordenador)),
      atualizar: vi.fn(() => of(coordenador)),
      ativar: vi.fn(() => of(undefined)),
      desativar: vi.fn(() => of(undefined))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre já nos dados, mais recentes primeiro, com a vigência', async () => {
    const { el } = await abrir();
    expect(servico['listar']).toHaveBeenCalledWith({ dataInicio: undefined, dataFim: undefined, ativo: null, buscaGlobal: undefined }, 0, 25, 'dataInicio,desc');
    expect(el.textContent).toContain('ANA LIMA');
    expect(el.textContent).toContain('Vigente');
    expect(chipsDoPainel(el)).toEqual([]);
  });

  it('restaura o recorte de datas da URL', async () => {
    const { el } = await abrir('/lista?inicio=2025-01-01&fim=2025-12-31');
    expect(servico['listar']).toHaveBeenLastCalledWith(expect.objectContaining({ dataInicio: '2025-01-01', dataFim: '2025-12-31' }), 0, 25, 'dataInicio,desc');
    expect(chipsDoPainel(el)).toEqual(['Início a partir de: 01/01/2025', 'Fim até: 31/12/2025']);
  });

  it('"n" abre o cadastro e bloqueia fim antes do início', async () => {
    const { componente } = await abrir();
    tecla('n');
    const tela = componente as unknown as {
      formularioAberto(): boolean;
      selecionar(id: string): void;
      inicio: { set(v: Date): void };
      fim: { set(v: Date | null): void };
      salvar(): void;
    };
    expect(tela.formularioAberto()).toBe(true);
    tela.selecionar('c1');
    tela.inicio.set(new Date(2025, 5, 10));
    tela.fim.set(new Date(2025, 0, 1));
    tela.salvar();
    expect(servico['criar']).not.toHaveBeenCalled();
    tela.fim.set(null);
    tela.salvar();
    expect(servico['criar']).toHaveBeenCalledWith({ idColaborador: 'c1', dataInicio: '2025-06-10', dataFim: null });
  });
});
