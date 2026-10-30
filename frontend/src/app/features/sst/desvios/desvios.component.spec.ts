import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { LookupStore } from '../../../core/lookup/store/lookup.store';
import { AuthService } from '../../../core/services/auth.service';
import { RhService } from '../../../core/services/rh.service';
import { SstService } from '../../../core/services/sst.service';
import { UsuariosService } from '../../../core/services/usuarios.service';
import { abrirListagem, chipsDoPainel, paginaTeste } from '../../../shared/listagem/listagem-teste';
import { DesviosComponent } from './desvios.component';
import { DesviosLookupsService } from './desvios-lookups.service';

const desvio = {
  id: 'd1',
  idInspecao: 'i1',
  dataInspecao: '2026-10-01T10:00:00Z',
  idFormulario: 'f1',
  idInspetor: 'u1',
  inspetor: 'ANA LIMA',
  baseOperacional: { id: 'b1', nome: 'JUAZEIRO' },
  equipe: { id: 'q1', codigo: 'EQ-01' },
  idPergunta: 'p1',
  pergunta: 'Usa EPI?',
  gravidade: 'CRITICA',
  descricao: 'Sem luva',
  acaoCorretiva: null,
  prazo: '2026-10-05',
  status: 'ABERTO',
  vencido: true,
  diasEmAtraso: 6,
  colaboradores: [{ id: 'c1', nomeCompleto: 'JOÃO SILVA' }],
  anexos: []
};

describe('DesviosComponent (SST)', () => {
  let sst: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = (url = '/lista') =>
    abrirListagem(DesviosComponent, 'lista', url, [
      { provide: SstService, useValue: sst },
      { provide: RhService, useValue: { listarEquipes: vi.fn(() => paginaTeste([])), listarVeiculos: vi.fn(() => paginaTeste([])) } },
      { provide: UsuariosService, useValue: { listarUsuarios: vi.fn(() => paginaTeste([])) } },
      { provide: LookupStore, useValue: { searchCollaborators: vi.fn(() => of({ items: [], hasMore: false })) } },
      { provide: AuthService, useValue: { currentUser: () => ({ permissoes: ['sst:inspecao:write'] }) } },
      {
        provide: DesviosLookupsService,
        useValue: {
          carregar: () =>
            of({ processos: [], bases: [{ id: 'b1', nome: 'JUAZEIRO' }], equipes: [{ id: 'q1', codigo: 'EQ-01' }], centrosCusto: [], formularios: [], veiculos: [] })
        }
      }
    ]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    sst = {
      listarDesvios: vi.fn(() => paginaTeste([desvio])),
      buscarDesvioPorId: vi.fn(() => of({ ...desvio, status: 'RESOLVIDO', vencido: false })),
      tratarDesvio: vi.fn(() => of(undefined))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre nos desvios em aberto (chip), pelo prazo mais próximo', async () => {
    const { el } = await abrir();
    expect(sst['listarDesvios']).toHaveBeenCalledWith(
      expect.objectContaining({ status: ['ABERTO', 'EM_TRATAMENTO'], page: 0, size: 25, sort: 'prazo,asc', buscaGlobal: null })
    );
    expect(chipsDoPainel(el)).toEqual(['Status: Em aberto']);
    expect(el.textContent).toContain('Usa EPI?');
    expect(el.textContent).toContain('6 dias em atraso');
  });

  it('restaura gravidade, base, prazo vencido e período de inspeção da URL', async () => {
    const { el } = await abrir('/lista?status=&gravidade=CRITICA,ALTA&base=b1&situacaoPrazo=VENCIDOS&inspecao=2026-10-01~2026-10-10');
    const filtro = sst['listarDesvios'].mock.lastCall![0];
    expect(filtro).toMatchObject({ status: null, gravidade: ['CRITICA', 'ALTA'], idBaseOperacional: ['b1'], apenasVencidos: true, semPrazo: null });
    expect(new Date(filtro.dataInspecaoAte).getHours()).toBe(23);
    expect(chipsDoPainel(el)).toEqual(['Status: Todos', 'Gravidade: 2 selecionadas', 'Prazo: Apenas vencidos', 'Base: JUAZEIRO', 'Inspeção: 01/10/2026 a 10/10/2026']);
  });

  it('linha tratada que sai do recorte some da página sem recarregar', async () => {
    const { componente } = await abrir();
    const tela = componente as unknown as { linhaAlterada(id: string): void; linhas(): unknown[]; total(): number };
    tela.linhaAlterada('d1');
    expect(tela.linhas()).toEqual([]);
    expect(tela.total()).toBe(0);
    expect(sst['listarDesvios']).toHaveBeenCalledTimes(1);
  });
});
