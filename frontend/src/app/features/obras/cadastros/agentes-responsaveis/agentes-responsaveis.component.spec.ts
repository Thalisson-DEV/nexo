import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthService } from '../../../../core/services/auth.service';
import { ObrasService } from '../../../../core/services/obras.service';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../../shared/listagem/listagem-teste';
import { AgentesResponsaveisComponent } from './agentes-responsaveis.component';

const registro = { id: 'a1', nome: 'CARLOS EDUARDO SILVA', email: 'carlos@concessionaria.com.br', telefone: '(74) 99999-1111', empresaOrgao: 'CONCESSIONARIA', ativo: true };

describe('AgentesResponsaveisComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  let permissoes: string[];
  const abrir = (url = '/lista') =>
    abrirListagem(AgentesResponsaveisComponent, 'lista', url, [
      { provide: ObrasService, useValue: servico },
      { provide: AuthService, useValue: { currentUser: () => ({ permissoes }) } }
    ]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    permissoes = ['obras:cadastros:read', 'obras:cadastros:write'];
    servico = {
      listarAgentes: vi.fn(() => paginaTeste([registro])),
      criarAgente: vi.fn(() => of(registro)),
      atualizarAgente: vi.fn(() => of(registro)),
      desativarAgente: vi.fn(() => of(null))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre já nos dados, paginado e ordenado no servidor', async () => {
    const { el } = await abrir();
    expect(servico['listarAgentes']).toHaveBeenCalledWith({ empresaOrgao: undefined, ativo: undefined, buscaGlobal: undefined, page: 0, size: 25, sort: 'nome,asc' });
    expect(el.textContent).toContain('CARLOS EDUARDO SILVA');
    expect(chipsDoPainel(el)).toEqual([]);
  });

  it('restaura os filtros da URL e mostra os chips', async () => {
    const { el } = await abrir('/lista?empresa=CONCESSIONARIA&status=true');
    expect(servico['listarAgentes']).toHaveBeenLastCalledWith(expect.objectContaining({ empresaOrgao: 'CONCESSIONARIA', ativo: true }));
    expect(chipsDoPainel(el)).toEqual(['Empresa: CONCESSIONARIA', 'Status: Ativo']);
  });

  it('"n" abre o cadastro e grava o nome em maiúsculas', async () => {
    const { componente } = await abrir();
    tecla('n');
    const tela = componente as unknown as { formularioAberto(): boolean; nome: { set(v: string): void }; email: { set(v: string): void }; salvar(): void };
    expect(tela.formularioAberto()).toBe(true);
    tela.nome.set('maria');
    tela.email.set('invalido');
    tela.salvar();
    expect(servico['criarAgente']).not.toHaveBeenCalled();
    tela.email.set('maria@x.com');
    tela.salvar();
    expect(servico['criarAgente']).toHaveBeenCalledWith(expect.objectContaining({ nome: 'MARIA', email: 'maria@x.com' }));
  });

  it('sem permissão de escrita não oferece cadastro, edição nem desativar', async () => {
    permissoes = ['obras:cadastros:read'];
    const { el } = await abrir();
    expect(el.textContent).not.toContain('Novo agente');
    expect(el.querySelector('button[aria-label^="Editar"]')).toBeNull();
    expect(el.querySelector('button[aria-label^="Desativar"]')).toBeNull();
  });
});
