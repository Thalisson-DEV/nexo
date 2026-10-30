import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { LookupStore } from '../../../core/lookup/store/lookup.store';
import { AuthService } from '../../../core/services/auth.service';
import { RhService } from '../../../core/services/rh.service';
import { SstService } from '../../../core/services/sst.service';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../shared/listagem/listagem-teste';
import { AtividadesSstComponent } from './atividades-sst.component';

const atividade = {
  id: 'a1',
  nome: 'Poda de árvores',
  descricao: 'Atividade em área operacional',
  idGestor: null,
  nomeGestor: null,
  funcaoGestor: null,
  idProcesso: null,
  nomeProcesso: null,
  ativo: true
};

describe('AtividadesSstComponent', () => {
  let sst: Record<string, ReturnType<typeof vi.fn>>;
  let permissoes: string[];
  const abrir = (url = '/lista') =>
    abrirListagem(AtividadesSstComponent, 'lista', url, [
      { provide: SstService, useValue: sst },
      { provide: RhService, useValue: { listarProcessosAtivos: vi.fn(() => paginaTeste([])) } },
      { provide: LookupStore, useValue: { searchCollaborators: vi.fn(() => of({ items: [], nextCursor: null, hasMore: false })) } },
      { provide: AuthService, useValue: { currentUser: () => ({ permissoes }) } }
    ]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    permissoes = ['sst:catalogo:read', 'sst:catalogo:write'];
    sst = {
      listarAtividadesSst: vi.fn(() => paginaTeste([atividade])),
      criarAtividadeSst: vi.fn(() => of(atividade)),
      atualizarAtividadeSst: vi.fn(() => of(atividade)),
      desativarAtividadeSst: vi.fn(() => of(null)),
      ativarAtividadeSst: vi.fn(() => of(null))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre nas atividades ativas e sinaliza a falta de gestor', async () => {
    const { el } = await abrir();
    expect(sst['listarAtividadesSst']).toHaveBeenCalledWith({ ativo: true, idGestor: null, buscaGlobal: null, page: 0, size: 25, sort: 'nome,asc' });
    expect(el.textContent).toContain('Poda de árvores');
    expect(el.textContent).toContain('Definir no evento');
    expect(chipsDoPainel(el)).toEqual(['Situação: Ativas']);
  });

  it('restaura o gestor e a situação da URL', async () => {
    await abrir('/lista?gestor=col-1&situacao=null');
    expect(sst['listarAtividadesSst']).toHaveBeenLastCalledWith(expect.objectContaining({ ativo: null, idGestor: 'col-1' }));
  });

  it('"n" abre o cadastro e salva gestor e processo opcionais', async () => {
    const { componente } = await abrir();
    tecla('n');
    const tela = componente as unknown as { formularioAberto(): boolean; nome: { set(v: string): void }; salvar(): void };
    expect(tela.formularioAberto()).toBe(true);
    tela.salvar();
    expect(sst['criarAtividadeSst']).not.toHaveBeenCalled();
    tela.nome.set('Detonação');
    tela.salvar();
    expect(sst['criarAtividadeSst']).toHaveBeenCalledWith({ nome: 'Detonação', descricao: null, idGestor: null, idProcesso: null });
  });

  it('sem permissão de escrita só consulta', async () => {
    permissoes = ['sst:catalogo:read'];
    const { el } = await abrir();
    expect(el.textContent).not.toContain('Nova atividade');
  });
});
