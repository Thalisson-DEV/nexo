import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { LookupStore } from '../../../core/lookup/store/lookup.store';
import { CoordenadorService } from '../../../core/services/coordenador.service';
import { ImportacaoService } from '../../../core/services/importacao.service';
import { RhService } from '../../../core/services/rh.service';
import { SupervisorService } from '../../../core/services/supervisor.service';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../shared/listagem/listagem-teste';
import { SupervisoresComponent } from './supervisores.component';

const colaborador = { id: 'c2', nomeCompleto: 'BRUNO REIS', cpf: '98765432100' };
const supervisor = {
  id: 's1',
  colaborador,
  coordenador: { id: 'k1', colaborador: { id: 'c1', nomeCompleto: 'ANA LIMA' } },
  dataInicio: '2025-01-01',
  dataFim: '2020-01-01',
  ativo: true
};

describe('SupervisoresComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = (url = '/lista') =>
    abrirListagem(SupervisoresComponent, 'lista', url, [
      { provide: SupervisorService, useValue: servico },
      { provide: CoordenadorService, useValue: { listarAtivosMin: vi.fn(() => of([{ id: 'k1', idColaborador: 'c1', nomeColaborador: 'ANA LIMA' }])) } },
      { provide: RhService, useValue: { buscarColaboradorPorId: vi.fn(() => of(colaborador)) } },
      { provide: LookupStore, useValue: { searchCollaborators: vi.fn(() => of({ items: [], hasMore: false })) } },
      { provide: ImportacaoService, useValue: { baixarModeloCsv: vi.fn() } }
    ]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    servico = {
      listar: vi.fn(() => paginaTeste([supervisor])),
      criar: vi.fn(() => of(supervisor)),
      atualizar: vi.fn(() => of(supervisor)),
      ativar: vi.fn(() => of(undefined)),
      desativar: vi.fn(() => of(undefined))
    };
  });

  afterEach(() => localStorage.clear());

  it('mostra o coordenador e a designação encerrada', async () => {
    const { el } = await abrir();
    expect(el.textContent).toContain('BRUNO REIS');
    expect(el.textContent).toContain('ANA LIMA');
    expect(el.textContent).toContain('Encerrado em 01/01/2020');
  });

  it('restaura o coordenador da URL e mostra o chip com o nome', async () => {
    const { el } = await abrir('/lista?coordenador=k1');
    expect(servico['listar']).toHaveBeenLastCalledWith(expect.objectContaining({ idCoordenador: 'k1' }), 0, 25, 'dataInicio,desc');
    expect(chipsDoPainel(el)).toEqual(['Coordenador: ANA LIMA']);
  });

  it('editar mantém colaborador e coordenador e grava as datas', async () => {
    const { componente } = await abrir();
    tecla('n');
    const tela = componente as unknown as { editar(s: unknown): void; idCoordenador: { set(v: string | null): void }; salvar(): void };
    tela.editar(supervisor);
    tela.salvar();
    expect(servico['criar']).not.toHaveBeenCalled();
    expect(servico['atualizar']).not.toHaveBeenCalled(); // fim antes do início
    tela.editar({ ...supervisor, dataFim: null });
    tela.idCoordenador.set(null);
    tela.salvar();
    expect(servico['atualizar']).not.toHaveBeenCalled();
    tela.idCoordenador.set('k1');
    tela.salvar();
    expect(servico['atualizar']).toHaveBeenCalledWith('s1', { idColaborador: 'c2', idCoordenador: 'k1', dataInicio: '2025-01-01', dataFim: null });
  });
});
