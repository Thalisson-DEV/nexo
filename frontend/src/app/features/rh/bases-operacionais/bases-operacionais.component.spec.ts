import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ImportacaoService } from '../../../core/services/importacao.service';
import { RhService } from '../../../core/services/rh.service';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../shared/listagem/listagem-teste';
import { BasesOperacionaisComponent } from './bases-operacionais.component';

const base = { id: 'b1', nome: 'JUAZEIRO', ativo: true, centroFaturamento: { id: 'c1', nome: 'CENTRO NORTE' }, latitude: -9.4, longitude: -40.5, raio: 500 };

describe('BasesOperacionaisComponent', () => {
  let rh: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = (url = '/lista') =>
    abrirListagem(BasesOperacionaisComponent, 'lista', url, [
      { provide: RhService, useValue: rh },
      { provide: ImportacaoService, useValue: { baixarModeloCsv: vi.fn() } }
    ]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    rh = {
      listarCentrosFaturamentoAtivos: vi.fn(() => paginaTeste([{ id: 'c1', nome: 'CENTRO NORTE' }])),
      listarBasesOperacionais: vi.fn(() => paginaTeste([base])),
      criarBaseOperacional: vi.fn(() => of(base)),
      atualizarBaseOperacional: vi.fn(() => of(base)),
      ativarBaseOperacional: vi.fn(() => of(null)),
      desativarBaseOperacional: vi.fn(() => of(null))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre já nos dados, paginado e ordenado no servidor', async () => {
    const { el } = await abrir();
    expect(rh['listarBasesOperacionais']).toHaveBeenCalledWith({ idCentroFaturamento: null, ativo: null, buscaGlobal: undefined, page: 0, size: 25, sort: 'nome,asc' });
    expect(el.textContent).toContain('JUAZEIRO');
    expect(el.textContent).toContain('500 m');
    expect(chipsDoPainel(el)).toEqual([]);
  });

  it('restaura o centro de custo da URL e mostra o chip com o nome', async () => {
    const { el } = await abrir('/lista?centro=c1');
    expect(rh['listarBasesOperacionais']).toHaveBeenLastCalledWith(expect.objectContaining({ idCentroFaturamento: 'c1' }));
    expect(chipsDoPainel(el)).toEqual(['Centro de custo: CENTRO NORTE']);
  });

  it('editar desativando grava os dados e depois desativa', async () => {
    const { componente } = await abrir();
    const tela = componente as unknown as { editar(b: unknown): void; ativo: { set(v: boolean): void }; salvar(): void };
    tela.editar(base);
    tela.ativo.set(false);
    tela.salvar();
    expect(rh['atualizarBaseOperacional']).toHaveBeenCalledWith('b1', expect.objectContaining({ nome: 'JUAZEIRO', centroCustoId: 'c1', raio: 500 }));
    expect(rh['desativarBaseOperacional']).toHaveBeenCalledWith('b1');
  });

  it('"n" abre o cadastro e exige nome e centro de custo', async () => {
    const { componente } = await abrir();
    tecla('n');
    const tela = componente as unknown as { formularioAberto(): boolean; nome: { set(v: string): void }; salvar(): void };
    expect(tela.formularioAberto()).toBe(true);
    tela.nome.set('Feira');
    tela.salvar();
    expect(rh['criarBaseOperacional']).not.toHaveBeenCalled();
  });
});
