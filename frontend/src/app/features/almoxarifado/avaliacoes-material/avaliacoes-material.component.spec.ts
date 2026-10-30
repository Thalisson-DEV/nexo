import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ImportacaoService } from '../../../core/services/importacao.service';
import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../shared/listagem/listagem-teste';
import { AvaliacoesMaterialComponent } from './avaliacoes-material.component';

const registro = { id: 'r1', nome: 'VALOR TESTE', ativo: true };

describe('AvaliacoesMaterialComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = (url = '/lista') =>
    abrirListagem(AvaliacoesMaterialComponent, 'lista', url, [
      { provide: AlmoxarifadoService, useValue: servico },
      { provide: ImportacaoService, useValue: { baixarModeloCsv: vi.fn() } }
    ]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    servico = {
      listarAvaliacoes: vi.fn(() => paginaTeste([registro])),
      criarAvaliacao: vi.fn(() => of({ ...registro, id: 'novo' })),
      atualizarAvaliacao: vi.fn(() => of(registro)),
      ativarAvaliacao: vi.fn(() => of(null)),
      desativarAvaliacao: vi.fn(() => of(null))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre já nos dados, sem etapa de pesquisa', async () => {
    const { el } = await abrir();
    expect(servico['listarAvaliacoes']).toHaveBeenCalledWith({ ativo: null, buscaGlobal: undefined, page: 0, size: 25, sort: 'nome,asc' });
    expect(el.textContent).toContain('VALOR TESTE');
    expect(chipsDoPainel(el)).toEqual([]);
  });

  it('restaura o filtro da URL e mostra o chip', async () => {
    const { el, componente } = await abrir('/lista?status=true&busca=val');
    expect(servico['listarAvaliacoes']).toHaveBeenLastCalledWith(expect.objectContaining({ ativo: true, buscaGlobal: 'val' }));
    expect(chipsDoPainel(el)).toEqual(['Status: Ativo']);
    (componente as unknown as { lista: { reset(): void } }).lista.reset();
    expect(servico['listarAvaliacoes']).toHaveBeenLastCalledWith(expect.objectContaining({ ativo: null }));
  });

  it('"n" abre o cadastro em drawer e salvar valida antes de enviar', async () => {
    const { componente } = await abrir();
    tecla('n');
    const tela = componente as unknown as { formularioAberto(): boolean; principal: { set(v: string): void }; salvar(): void };
    expect(tela.formularioAberto()).toBe(true);
    tela.salvar();
    expect(servico['criarAvaliacao']).not.toHaveBeenCalled();
    tela.principal.set('Valor novo');
    tela.salvar();
    expect(servico['criarAvaliacao']).toHaveBeenCalledWith(expect.objectContaining({ nome: 'Valor novo' }));
  });
});
