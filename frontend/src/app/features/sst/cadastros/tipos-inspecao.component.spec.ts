import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthService } from '../../../core/services/auth.service';
import { SstService } from '../../../core/services/sst.service';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../shared/listagem/listagem-teste';
import { TiposInspecaoComponent } from './tipos-inspecao.component';

const item = { id: 't1', nome: 'CAMPO', descricao: null, ordem: 1, ativo: true };

describe('TiposInspecaoComponent', () => {
  let sst: Record<string, ReturnType<typeof vi.fn>>;
  let permissoes: string[];
  const abrir = (url = '/lista') =>
    abrirListagem(
      TiposInspecaoComponent,
      'lista',
      url,
      [
        { provide: SstService, useValue: sst },
        { provide: AuthService, useValue: { currentUser: () => ({ permissoes }) } }
      ]
    );

  beforeEach(() => {
    TestBed.resetTestingModule();
    permissoes = ['sst:catalogo:read', 'sst:catalogo:write'];
    sst = {
      listarTiposInspecao: vi.fn(() => paginaTeste([item])),
      criarTipoInspecao: vi.fn(() => of(item)),
      atualizarTipoInspecao: vi.fn(() => of(item)),
      desativarTipoInspecao: vi.fn(() => of(null)),
      ativarTipoInspecao: vi.fn(() => of(null))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre nos tipos ativos, paginado no servidor', async () => {
    const { el } = await abrir();
    expect(sst['listarTiposInspecao']).toHaveBeenCalledWith({ ativo: true, buscaGlobal: null, page: 0, size: 25, sort: 'ordem,asc' });
    expect(el.querySelector('h1')!.textContent).toBe('Tipos de inspeção');
    expect(chipsDoPainel(el)).toEqual(['Status: Ativos']);
    expect(el.textContent).not.toContain('filtro ativo');
  });

  it('o status da URL troca o recorte e "Limpar" volta aos ativos', async () => {
    const { componente } = await abrir('/lista?status=false');
    expect(sst['listarTiposInspecao']).toHaveBeenLastCalledWith(expect.objectContaining({ ativo: false }));
    (componente as unknown as { lista: { reset(): void } }).lista.reset();
    expect(sst['listarTiposInspecao']).toHaveBeenLastCalledWith(expect.objectContaining({ ativo: true }));
  });

  it('"n" abre o cadastro e o novo tipo entra no fim da ordem', async () => {
    const { componente } = await abrir();
    tecla('n');
    const tela = componente as unknown as { formularioAberto(): boolean; nome: { set(v: string): void }; salvar(): void };
    expect(tela.formularioAberto()).toBe(true);
    tela.salvar();
    expect(sst['criarTipoInspecao']).not.toHaveBeenCalled();
    tela.nome.set('Pé');
    tela.salvar();
    expect(sst['criarTipoInspecao']).toHaveBeenCalledWith({ nome: 'Pé', descricao: null, ordem: 2 });
  });

  it('sem permissão de escrita só consulta', async () => {
    permissoes = ['sst:catalogo:read'];
    const { el } = await abrir();
    expect(el.textContent).not.toContain('Novo tipo');
    expect(el.querySelector('button[aria-label^="Desativar"]')).toBeNull();
  });
});
