import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthService } from '../../../../core/services/auth.service';
import { ObrasService } from '../../../../core/services/obras.service';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../../shared/listagem/listagem-teste';
import { TiposAnexosComponent } from './tipos-anexos.component';

const registro = { id: 't1', nome: 'PROJETO EXECUTIVO', descricao: 'Projeto aprovado', ativo: true };

describe('TiposAnexosComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  let permissoes: string[];
  const abrir = (url = '/lista') =>
    abrirListagem(TiposAnexosComponent, 'lista', url, [
      { provide: ObrasService, useValue: servico },
      { provide: AuthService, useValue: { currentUser: () => ({ permissoes }) } }
    ]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    permissoes = ['obras:cadastros:read', 'obras:cadastros:write'];
    servico = {
      listarTiposAnexos: vi.fn(() => paginaTeste([registro])),
      criarTipoAnexo: vi.fn(() => of(registro)),
      atualizarTipoAnexo: vi.fn(() => of(registro)),
      desativarTipoAnexo: vi.fn(() => of(null))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre já nos dados, paginado e ordenado no servidor', async () => {
    const { el } = await abrir();
    expect(servico['listarTiposAnexos']).toHaveBeenCalledWith({ ativo: undefined, buscaGlobal: undefined, page: 0, size: 25, sort: 'nome,asc' });
    expect(el.textContent).toContain('PROJETO EXECUTIVO');
    expect(chipsDoPainel(el)).toEqual([]);
  });

  it('restaura os filtros da URL e mostra os chips', async () => {
    const { el } = await abrir('/lista?status=false');
    expect(servico['listarTiposAnexos']).toHaveBeenLastCalledWith(expect.objectContaining({ ativo: false }));
    expect(chipsDoPainel(el)).toEqual(['Status: Inativo']);
  });

  it('"n" abre o cadastro e grava o nome em maiúsculas', async () => {
    const { componente } = await abrir();
    tecla('n');
    const tela = componente as unknown as { formularioAberto(): boolean; nome: { set(v: string): void }; salvar(): void };
    expect(tela.formularioAberto()).toBe(true);
    tela.salvar();
    expect(servico['criarTipoAnexo']).not.toHaveBeenCalled();
    tela.nome.set('maria');
    tela.salvar();
    expect(servico['criarTipoAnexo']).toHaveBeenCalledWith(expect.objectContaining({ nome: 'MARIA' }));
  });

  it('sem permissão de escrita não oferece cadastro, edição nem desativar', async () => {
    permissoes = ['obras:cadastros:read'];
    const { el } = await abrir();
    expect(el.textContent).not.toContain('Novo tipo');
    expect(el.querySelector('button[aria-label^="Editar"]')).toBeNull();
    expect(el.querySelector('button[aria-label^="Desativar"]')).toBeNull();
  });
});
