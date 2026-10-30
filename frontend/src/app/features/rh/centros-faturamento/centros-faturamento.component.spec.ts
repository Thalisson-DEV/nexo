import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { RhService } from '../../../core/services/rh.service';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../shared/listagem/listagem-teste';
import { CentrosFaturamentoComponent } from './centros-faturamento.component';

const registro = { id: 'x1', nome: 'CENTRO NORTE', ativo: true };

describe('CentrosFaturamentoComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = (url = '/lista') => abrirListagem(CentrosFaturamentoComponent, 'lista', url, [{ provide: RhService, useValue: servico }]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    servico = {
      listarCentrosFaturamento: vi.fn(() => paginaTeste([registro])),
      criarCentroFaturamento: vi.fn(() => of(registro)),
      atualizarCentroFaturamento: vi.fn(() => of(registro)),
      ativarCentroFaturamento: vi.fn(() => of(null)),
      desativarCentroFaturamento: vi.fn(() => of(null))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre já nos dados, paginado e ordenado no servidor', async () => {
    const { el } = await abrir();
    expect(servico['listarCentrosFaturamento']).toHaveBeenCalledWith({ ativo: null, buscaGlobal: undefined, page: 0, size: 25, sort: 'nome,asc' });
    expect(el.textContent).toContain('CENTRO NORTE');
    expect(chipsDoPainel(el)).toEqual([]);
  });

  it('restaura o filtro da URL e "Limpar" volta ao padrão', async () => {
    const { el, componente } = await abrir('/lista?status=true&busca=mau');
    expect(servico['listarCentrosFaturamento']).toHaveBeenLastCalledWith(expect.objectContaining({ ativo: true, buscaGlobal: 'mau' }));
    expect(chipsDoPainel(el)).toEqual(['Status: Ativo']);
    (componente as unknown as { lista: { reset(): void } }).lista.reset();
    expect(servico['listarCentrosFaturamento']).toHaveBeenLastCalledWith(expect.objectContaining({ ativo: null }));
  });

  it('"n" abre o cadastro em drawer e salvar valida antes de enviar', async () => {
    const { componente } = await abrir();
    tecla('n');
    const tela = componente as unknown as { formularioAberto(): boolean; descricao: { set(v: string): void }; salvar(): void };
    expect(tela.formularioAberto()).toBe(true);
    tela.salvar();
    expect(servico['criarCentroFaturamento']).not.toHaveBeenCalled();
    tela.descricao.set('Perda');
    tela.salvar();
    expect(servico['criarCentroFaturamento']).toHaveBeenCalledWith({ nome: 'Perda', ativo: true });
    expect(servico['listarCentrosFaturamento']).toHaveBeenCalledTimes(2);
  });
});
