import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ImportacaoService } from '../../../core/services/importacao.service';
import { PerfisAcessoService } from '../../../core/services/perfis-acesso.service';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../shared/listagem/listagem-teste';
import { PerfisAcessoComponent } from './perfis-acesso.component';

const permissoes = [
  { id: 'r', nome: 'frota:veiculo:read', ativo: true },
  { id: 'w', nome: 'frota:veiculo:write', ativo: true },
  { id: 'v', nome: 'desconto:validar', ativo: true },
  { id: 'x', nome: 'admin', ativo: true }
];
const perfil = { id: 'p1', nome: 'GESTOR', ativo: true };

describe('PerfisAcessoComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = (url = '/lista') =>
    abrirListagem(PerfisAcessoComponent, 'lista', url, [
      { provide: PerfisAcessoService, useValue: servico },
      { provide: ImportacaoService, useValue: { baixarModeloCsv: vi.fn() } }
    ]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    servico = {
      listarPerfis: vi.fn(() => paginaTeste([perfil])),
      listarTodasPermissoes: vi.fn(() => paginaTeste(permissoes)),
      listarPermissoesPorPerfil: vi.fn(() => of([permissoes[0]])),
      criarPerfil: vi.fn(() => of({ ...perfil, id: 'novo' })),
      atualizarPerfil: vi.fn(() => of(perfil)),
      definirPermissoes: vi.fn(() => of([])),
      ativarPerfil: vi.fn(() => of(null)),
      desativarPerfil: vi.fn(() => of(null))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre já nos dados, ordenado pelo nome', async () => {
    const { el } = await abrir();
    expect(servico['listarPerfis']).toHaveBeenCalledWith({ ativo: null, buscaGlobal: undefined, page: 0, size: 25, sort: 'nome,asc' });
    expect(el.textContent).toContain('GESTOR');
    expect(chipsDoPainel(el)).toEqual([]);
  });

  it('editar grava todas as permissões numa chamada só, sem regravar o nome igual', async () => {
    const { componente } = await abrir();
    const tela = componente as unknown as { editar(p: unknown): void; selecionadas: { set(v: Set<string>): void }; salvar(): void };
    tela.editar(perfil);
    tela.selecionadas.set(new Set(['w', 'v']));
    tela.salvar();
    expect(servico['atualizarPerfil']).not.toHaveBeenCalled();
    expect(servico['definirPermissoes']).toHaveBeenCalledTimes(1);
    expect(servico['definirPermissoes']).toHaveBeenCalledWith('p1', ['w', 'v']);
    expect(servico['ativarPerfil']).not.toHaveBeenCalled();
  });

  it('sem mudança nas permissões não chama o servidor para elas', async () => {
    const { componente } = await abrir();
    const tela = componente as unknown as { editar(p: unknown): void; nome: { set(v: string): void }; salvar(): void };
    tela.editar(perfil);
    tela.nome.set('GESTOR 2');
    tela.salvar();
    expect(servico['atualizarPerfil']).toHaveBeenCalledWith('p1', { nome: 'GESTOR 2' });
    expect(servico['definirPermissoes']).not.toHaveBeenCalled();
  });

  it('copia as permissões de outro perfil como ponto de partida', async () => {
    const { componente } = await abrir();
    const tela = componente as unknown as {
      novo(): void; perfilParaCopiar: { set(v: string): void }; copiarPermissoes(): void; selecionadas(): Set<string>;
    };
    tela.novo();
    tela.perfilParaCopiar.set('p1');
    tela.copiarPermissoes();
    expect(servico['listarPermissoesPorPerfil']).toHaveBeenCalledWith('p1');
    expect([...tela.selecionadas()]).toEqual(['r']);
  });

  it('"n" cria o perfil e vincula as marcadas', async () => {
    const { componente } = await abrir();
    tecla('n');
    const tela = componente as unknown as { nome: { set(v: string): void }; selecionadas: { set(v: Set<string>): void }; salvar(): void };
    tela.salvar();
    expect(servico['criarPerfil']).not.toHaveBeenCalled();
    tela.nome.set('Auditor');
    tela.selecionadas.set(new Set(['r']));
    tela.salvar();
    expect(servico['criarPerfil']).toHaveBeenCalledWith({ nome: 'Auditor' });
    expect(servico['definirPermissoes']).toHaveBeenCalledWith('novo', ['r']);
  });
});
