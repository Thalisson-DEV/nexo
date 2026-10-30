import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { RhDescontosService } from '../../../core/services/rh-descontos.service';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../shared/listagem/listagem-teste';
import { MotivosDescontoComponent } from './motivos-desconto.component';

const registro = { id: 'x1', descricao: 'MAU USO', tetoParcelas: 6, ativo: true };

describe('MotivosDescontoComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = (url = '/lista') => abrirListagem(MotivosDescontoComponent, 'lista', url, [{ provide: RhDescontosService, useValue: servico }]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    servico = {
      listarMotivos: vi.fn(() => paginaTeste([registro])),
      criarMotivo: vi.fn(() => of(registro)),
      atualizarMotivo: vi.fn(() => of(registro)),
      ativarMotivo: vi.fn(() => of(null)),
      desativarMotivo: vi.fn(() => of(null))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre já nos dados, paginado e ordenado no servidor', async () => {
    const { el } = await abrir();
    expect(servico['listarMotivos']).toHaveBeenCalledWith({ ativo: null, buscaGlobal: undefined, page: 0, size: 25, sort: 'descricao,asc' });
    expect(el.textContent).toContain('MAU USO');
    expect(chipsDoPainel(el)).toEqual([]);
  });

  it('restaura o filtro da URL e "Limpar" volta ao padrão', async () => {
    const { el, componente } = await abrir('/lista?status=true&busca=mau');
    expect(servico['listarMotivos']).toHaveBeenLastCalledWith(expect.objectContaining({ ativo: true, buscaGlobal: 'mau' }));
    expect(chipsDoPainel(el)).toEqual(['Status: Ativo']);
    (componente as unknown as { lista: { reset(): void } }).lista.reset();
    expect(servico['listarMotivos']).toHaveBeenLastCalledWith(expect.objectContaining({ ativo: null }));
  });

  it('"n" abre o cadastro em drawer e salvar valida antes de enviar', async () => {
    const { componente } = await abrir();
    tecla('n');
    const tela = componente as unknown as { formularioAberto(): boolean; descricao: { set(v: string): void }; salvar(): void };
    expect(tela.formularioAberto()).toBe(true);
    tela.salvar();
    expect(servico['criarMotivo']).not.toHaveBeenCalled();
    tela.descricao.set('Perda');
    tela.salvar();
    expect(servico['criarMotivo']).toHaveBeenCalledWith(expect.objectContaining({ descricao: 'Perda' }));
    expect(servico['listarMotivos']).toHaveBeenCalledTimes(2);
  });
});
