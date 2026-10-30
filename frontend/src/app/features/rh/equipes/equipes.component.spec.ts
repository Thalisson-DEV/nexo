import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { LookupStore } from '../../../core/lookup/store/lookup.store';
import { CoordenadorMinResponse, SupervisorMinResponse } from '../../../core/models/rh.models';
import { ContratoService } from '../../../core/services/contrato.service';
import { ImportacaoService } from '../../../core/services/importacao.service';
import { RhService } from '../../../core/services/rh.service';
import { CesConfirmationService } from '@ces/ui';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../shared/listagem/listagem-teste';
import { EquipesComponent, MembroEquipe } from './equipes.component';

const coordenadores: CoordenadorMinResponse[] = [
  { id: 'coord-1', idColaborador: 'colab-1', nomeColaborador: 'Carlos Coordenador' },
  { id: 'coord-2', idColaborador: 'colab-2', nomeColaborador: 'Marcos Coordenador' }
];
const supervisores: SupervisorMinResponse[] = [
  { id: 'sup-1', idColaborador: 'colab-3', nomeColaborador: 'Silvia Supervisora', idCoordenador: 'coord-1', nomeCoordenador: 'Carlos Coordenador' },
  { id: 'sup-2', idColaborador: 'colab-4', nomeColaborador: 'Sergio Supervisor', idCoordenador: 'coord-2', nomeCoordenador: 'Marcos Coordenador' }
];
const colaboradores = [
  ['colab-1', 'Carlos Coordenador'],
  ['colab-3', 'Silvia Supervisora'],
  ['colab-5', 'Eletricista Líder'],
  ['colab-6', 'Eletricista Ajudante']
].map(([value, label]) => ({ value, label }));
const equipe = {
  id: 'e1',
  codigo: 'EQ-01',
  tipoEquipe: { id: 't1', nome: 'LINHA VIVA' },
  baseOperacional: { id: 'b1', nome: 'JUAZEIRO' },
  processo: { id: 'p1', nome: 'MANUTENÇÃO' },
  veiculo: { id: 'v1', placa: 'ABC1234', modelo: 'HILUX' },
  coordenador: { id: 'coord-1', colaborador: { id: 'colab-1', nomeCompleto: 'Carlos Coordenador' } },
  supervisor: { id: 'sup-1', colaborador: { id: 'colab-3', nomeCompleto: 'Silvia Supervisora' } },
  idContrato: null,
  ativo: true,
  metaFaturamentoMensal: 15000,
  membros: [
    { id: 'm1', idColaborador: 'colab-5', nomeColaborador: 'Eletricista Líder', funcao: 'RESPONSAVEL', vinculoAtivo: true },
    { id: 'm2', idColaborador: 'colab-6', nomeColaborador: 'Eletricista Ajudante', funcao: 'PARCEIRO', vinculoAtivo: true },
    { id: 'm3', idColaborador: 'colab-9', nomeColaborador: 'Saiu da equipe', funcao: 'PARCEIRO', vinculoAtivo: false }
  ]
};

interface Tela {
  formularioAberto(): boolean;
  editar(e: unknown): void;
  escolherCoordenador(id: string | null): void;
  idSupervisor: { (): string | null; set(v: string | null): void };
  supervisoresDisponiveis(): { value: string }[];
  colaboradoresParaMembro(): { value: string }[];
  colaboradores: { search(q: string): void };
  membros: { (): MembroEquipe[]; set(v: MembroEquipe[]): void };
  novoMembro: { set(v: string | null): void };
  novaFuncao: { set(v: string | null): void };
  adicionarMembro(): Promise<void>;
  salvar(): void;
}

describe('EquipesComponent', () => {
  let rh: Record<string, ReturnType<typeof vi.fn>>;
  let confirmar: ReturnType<typeof vi.fn>;
  const abrir = async (url = '/lista') => {
    const r = await abrirListagem(EquipesComponent, 'lista', url, [
      { provide: RhService, useValue: rh },
      { provide: ContratoService, useValue: { listarContratosAtivos: vi.fn(() => paginaTeste([])) } },
      { provide: ImportacaoService, useValue: { baixarModeloCsv: vi.fn() } },
      { provide: LookupStore, useValue: { searchCollaborators: vi.fn(() => of({ items: colaboradores, nextCursor: null, hasMore: false })) } }
    ]);
    confirmar = vi.spyOn(TestBed.inject(CesConfirmationService), 'confirm').mockResolvedValue(true) as unknown as ReturnType<typeof vi.fn>;
    return { ...r, tela: r.componente as unknown as Tela };
  };

  beforeEach(() => {
    TestBed.resetTestingModule();
    rh = {
      listarBasesOperacionaisAtivas: vi.fn(() => paginaTeste([{ id: 'b1', nome: 'JUAZEIRO' }])),
      listarTiposEquipeAtivos: vi.fn(() => paginaTeste([{ id: 't1', nome: 'LINHA VIVA' }])),
      listarProcessosAtivos: vi.fn(() => paginaTeste([{ id: 'p1', nome: 'MANUTENÇÃO' }])),
      listarCoordenadoresAtivosMin: vi.fn(() => of(coordenadores)),
      listarSupervisoresAtivosMin: vi.fn(() => of(supervisores)),
      listarVeiculos: vi.fn(() => paginaTeste([equipe.veiculo])),
      buscarVeiculoPorId: vi.fn(() => of(equipe.veiculo)),
      buscarColaboradorPorId: vi.fn(() => of({ id: 'colab-5', nomeCompleto: 'Eletricista Líder' })),
      listarEquipes: vi.fn(() => paginaTeste([equipe])),
      criarEquipe: vi.fn(() => of(equipe)),
      atualizarEquipe: vi.fn(() => of(equipe)),
      ativarEquipe: vi.fn(() => of(null)),
      desativarEquipe: vi.fn(() => of(null))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre já nos dados, ordenada pelo código, mostrando o responsável', async () => {
    const { el } = await abrir();
    expect(rh['listarEquipes']).toHaveBeenCalledWith(expect.objectContaining({ ativo: null, buscaGlobal: undefined, page: 0, size: 25, sort: 'codigo,asc' }));
    expect(el.textContent).toContain('EQ-01');
    expect(el.textContent).toContain('Responsável: Eletricista Líder');
    expect(chipsDoPainel(el)).toEqual([]);
  });

  it('restaura coordenador, veículo e responsável da URL com os nomes nos chips', async () => {
    const { el } = await abrir('/lista?coordenador=coord-1&veiculo=v1&responsavel=colab-5');
    expect(rh['listarEquipes']).toHaveBeenLastCalledWith(expect.objectContaining({ idCoordenador: 'coord-1', idVeiculo: 'v1', idResponsavel: 'colab-5' }));
    expect(chipsDoPainel(el)).toEqual(['Veículo: ABC1234', 'Coordenador: Carlos Coordenador', 'Responsável: Eletricista Líder']);
  });

  it('o supervisor depende do coordenador e é limpo quando não responde a ele', async () => {
    const { tela } = await abrir();
    tecla('n');
    tela.escolherCoordenador('coord-1');
    expect(tela.supervisoresDisponiveis().map((s) => s.value)).toEqual(['sup-1']);
    tela.idSupervisor.set('sup-1');
    tela.escolherCoordenador('coord-2');
    expect(tela.idSupervisor()).toBeNull();
    expect(tela.supervisoresDisponiveis().map((s) => s.value)).toEqual(['sup-2']);
  });

  it('coordenador e supervisor da equipe não aparecem para virar membro', async () => {
    const { tela } = await abrir();
    tela.editar(equipe);
    tela.colaboradores.search('el');
    await new Promise((r) => setTimeout(r, 400));
    expect(tela.colaboradoresParaMembro().map((c) => c.value)).toEqual([]);
    tela.membros.set([]);
    expect(tela.colaboradoresParaMembro().map((c) => c.value)).toEqual(['colab-5', 'colab-6']);
  });

  it('exige um responsável antes de salvar', async () => {
    const { tela } = await abrir();
    tela.editar(equipe);
    expect(tela.membros().map((m) => m.idColaborador)).toEqual(['colab-5', 'colab-6']);
    tela.membros.set([{ idColaborador: 'colab-6', nome: 'Eletricista Ajudante', funcao: 'PARCEIRO' }]);
    tela.salvar();
    expect(rh['atualizarEquipe']).not.toHaveBeenCalled();

    tela.colaboradores.search('el');
    await new Promise((r) => setTimeout(r, 400));
    tela.novoMembro.set('colab-5');
    tela.novaFuncao.set('RESPONSAVEL');
    await tela.adicionarMembro();
    expect(confirmar).not.toHaveBeenCalled();
    tela.salvar();
    expect(rh['atualizarEquipe']).toHaveBeenCalledWith(
      'e1',
      expect.objectContaining({
        membros: [
          { idColaborador: 'colab-6', funcao: 'PARCEIRO' },
          { idColaborador: 'colab-5', funcao: 'RESPONSAVEL' }
        ]
      })
    );
  });

  it('a meta não vai mais no cadastro da equipe (tem tela própria)', async () => {
    const { tela } = await abrir();
    tela.editar(equipe);
    tela.salvar();
    const request = (rh['atualizarEquipe'] as ReturnType<typeof vi.fn>).mock.calls[0][1];
    expect(request).not.toHaveProperty('metaFaturamentoMensal');
    expect(request).toEqual(expect.objectContaining({ idVeiculo: 'v1', idSupervisor: 'sup-1' }));
  });
});
