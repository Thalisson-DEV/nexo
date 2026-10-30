import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthService } from '../../../../core/services/auth.service';
import { ObrasService } from '../../../../core/services/obras.service';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../../shared/listagem/listagem-teste';
import { StatusObrasComponent } from './status-obras.component';

const registro = { id: 's1', nome: 'EM EXECUÇÃO', ordem: 2, concluida: false, ativo: true };

describe('StatusObrasComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  let permissoes: string[];
  const abrir = (url = '/lista') =>
    abrirListagem(StatusObrasComponent, 'lista', url, [
      { provide: ObrasService, useValue: servico },
      { provide: AuthService, useValue: { currentUser: () => ({ permissoes }) } }
    ]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    permissoes = ['obras:cadastros:read', 'obras:cadastros:write'];
    servico = {
      listarStatus: vi.fn(() => paginaTeste([registro])),
      criarStatus: vi.fn(() => of(registro)),
      atualizarStatus: vi.fn(() => of(registro)),
      desativarStatus: vi.fn(() => of(null))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre já nos dados, paginado e ordenado no servidor', async () => {
    const { el } = await abrir();
    expect(servico['listarStatus']).toHaveBeenCalledWith({ concluida: undefined, ativo: undefined, buscaGlobal: undefined, page: 0, size: 25, sort: 'ordem,asc' });
    expect(el.textContent).toContain('EM EXECUÇÃO');
    expect(chipsDoPainel(el)).toEqual([]);
  });

  it('restaura os filtros da URL e mostra os chips', async () => {
    const { el } = await abrir('/lista?tipo=true&status=true');
    expect(servico['listarStatus']).toHaveBeenLastCalledWith(expect.objectContaining({ concluida: true, ativo: true }));
    expect(chipsDoPainel(el)).toEqual(['Tipo: Concluída / finalizada', 'Status: Ativo']);
  });

  it('"n" abre o cadastro e grava o nome em maiúsculas', async () => {
    const { componente } = await abrir();
    tecla('n');
    const tela = componente as unknown as { formularioAberto(): boolean; nome: { set(v: string): void }; salvar(): void };
    expect(tela.formularioAberto()).toBe(true);
    tela.salvar();
    expect(servico['criarStatus']).not.toHaveBeenCalled();
    tela.nome.set('maria');
    tela.salvar();
    expect(servico['criarStatus']).toHaveBeenCalledWith(expect.objectContaining({ nome: 'MARIA' }));
  });

  it('sem permissão de escrita não oferece cadastro, edição nem desativar', async () => {
    permissoes = ['obras:cadastros:read'];
    const { el } = await abrir();
    expect(el.textContent).not.toContain('Novo status');
    expect(el.querySelector('button[aria-label^="Editar"]')).toBeNull();
    expect(el.querySelector('button[aria-label^="Desativar"]')).toBeNull();
  });
});
