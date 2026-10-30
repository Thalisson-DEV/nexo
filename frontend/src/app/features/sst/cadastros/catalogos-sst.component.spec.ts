import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthService } from '../../../core/services/auth.service';
import { SstService } from '../../../core/services/sst.service';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../shared/listagem/listagem-teste';
import { CatalogosSstComponent } from './catalogos-sst.component';

const item = { id: 'c1', tipo: 'LOCAL_LESAO', nome: 'MÃO', descricao: null, codigo: null, ordem: 1, ativo: true };

describe('CatalogosSstComponent', () => {
  let sst: Record<string, ReturnType<typeof vi.fn>>;
  let permissoes: string[];
  const abrir = (url = '/lista') =>
    abrirListagem(
      CatalogosSstComponent,
      'lista',
      url,
      [
        { provide: SstService, useValue: sst },
        { provide: AuthService, useValue: { currentUser: () => ({ permissoes }) } }
      ],
      { tipoCatalogo: 'LOCAL_LESAO' }
    );

  beforeEach(() => {
    TestBed.resetTestingModule();
    permissoes = ['sst:catalogo:read', 'sst:catalogo:write'];
    sst = {
      listarCatalogosSst: vi.fn(() => paginaTeste([item])),
      criarCatalogoSst: vi.fn(() => of(item)),
      atualizarCatalogoSst: vi.fn(() => of(item)),
      desativarCatalogoSst: vi.fn(() => of(null)),
      ativarCatalogoSst: vi.fn(() => of(null))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre nos ativos do tipo da rota, paginado no servidor', async () => {
    const { el } = await abrir();
    expect(sst['listarCatalogosSst']).toHaveBeenCalledWith({ tipo: 'LOCAL_LESAO', ativo: true, buscaGlobal: null, page: 0, size: 25, sort: 'ordem,asc' });
    expect(el.querySelector('h1')!.textContent).toBe('Locais de Lesão');
    expect(chipsDoPainel(el)).toEqual(['Situação: Ativos']);
    expect(el.textContent).not.toContain('filtro ativo');
  });

  it('a situação da URL troca o recorte e "Limpar" volta aos ativos', async () => {
    const { componente } = await abrir('/lista?situacao=false');
    expect(sst['listarCatalogosSst']).toHaveBeenLastCalledWith(expect.objectContaining({ ativo: false }));
    (componente as unknown as { lista: { reset(): void } }).lista.reset();
    expect(sst['listarCatalogosSst']).toHaveBeenLastCalledWith(expect.objectContaining({ ativo: true }));
  });

  it('"n" abre o cadastro e salva com o tipo da rota', async () => {
    const { componente } = await abrir();
    tecla('n');
    const tela = componente as unknown as { formularioAberto(): boolean; nome: { set(v: string): void }; salvar(): void };
    expect(tela.formularioAberto()).toBe(true);
    tela.salvar();
    expect(sst['criarCatalogoSst']).not.toHaveBeenCalled();
    tela.nome.set('Pé');
    tela.salvar();
    expect(sst['criarCatalogoSst']).toHaveBeenCalledWith({ tipo: 'LOCAL_LESAO', nome: 'Pé', descricao: null, codigo: null, ordem: 0 });
  });

  it('sem permissão de escrita só consulta', async () => {
    permissoes = ['sst:catalogo:read'];
    const { el } = await abrir();
    expect(el.textContent).not.toContain('Novo local de lesão');
    expect(el.querySelector('button[aria-label^="Desativar"]')).toBeNull();
  });
});
