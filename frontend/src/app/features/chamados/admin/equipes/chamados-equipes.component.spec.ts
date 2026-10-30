import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ChamadosService } from '../../../../core/services/chamados.service';
import { UsuariosService } from '../../../../core/services/usuarios.service';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../../shared/listagem/listagem-teste';
import { ChamadosEquipesComponent } from './chamados-equipes.component';

const equipe = { id: 'e1', codigo: 'INFRA', nome: 'Infraestrutura', descricao: null, membros: ['u1'], ativo: true };

describe('ChamadosEquipesComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = (url = '/lista') =>
    abrirListagem(ChamadosEquipesComponent, 'lista', url, [
      { provide: ChamadosService, useValue: servico },
      {
        provide: UsuariosService,
        useValue: {
          listarUsuarios: vi.fn(() => paginaTeste([])),
          buscarUsuarioPorId: vi.fn(() => of({ id: 'u1', login: 'maria.souza' }))
        }
      }
    ]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    servico = {
      listarEquipes: vi.fn(() => paginaTeste([equipe])),
      buscarEquipe: vi.fn(() => of(equipe)),
      criarEquipe: vi.fn(() => of(equipe)),
      atualizarEquipe: vi.fn(() => of(equipe)),
      alternarEquipe: vi.fn(() => of(null))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre nas equipes ativas e mostra o login dos membros', async () => {
    const { el, harness } = await abrir();
    harness.detectChanges();
    expect(servico['listarEquipes']).toHaveBeenCalledWith({ buscaGlobal: undefined, codigo: undefined, ativo: true, page: 0, size: 25, sort: 'nome,asc' });
    expect(el.textContent).toContain('Infraestrutura');
    expect(el.textContent).toContain('maria.souza');
    expect(chipsDoPainel(el)).toEqual(['Status: Ativas']);
  });

  it('restaura código e status da URL', async () => {
    const { el } = await abrir('/lista?codigo=INFRA&ativo=null');
    expect(servico['listarEquipes']).toHaveBeenLastCalledWith(expect.objectContaining({ codigo: 'INFRA', ativo: undefined }));
    expect(chipsDoPainel(el)).toEqual(['Código: INFRA', 'Status: Todas']);
  });

  it('"n" abre o cadastro e grava o código em maiúsculas', async () => {
    const { componente } = await abrir();
    tecla('n');
    const tela = componente as unknown as { formularioAberto(): boolean; codigo: { set(v: string): void }; nome: { set(v: string): void }; salvar(): void };
    expect(tela.formularioAberto()).toBe(true);
    tela.salvar();
    expect(servico['criarEquipe']).not.toHaveBeenCalled();
    tela.codigo.set('suporte');
    tela.nome.set('Suporte');
    tela.salvar();
    expect(servico['criarEquipe']).toHaveBeenCalledWith({ codigo: 'SUPORTE', nome: 'Suporte', descricao: '', membros: [] });
  });
});
