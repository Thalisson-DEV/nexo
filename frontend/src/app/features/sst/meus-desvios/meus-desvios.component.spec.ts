import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthService } from '../../../core/services/auth.service';
import { CentrosCustoLookupService } from '../../../core/services/centros-custo-lookup.service';
import { RhService } from '../../../core/services/rh.service';
import { SstService } from '../../../core/services/sst.service';
import { abrirListagem, chipsDoPainel, paginaTeste } from '../../../shared/listagem/listagem-teste';
import { MeusDesviosComponent } from './meus-desvios.component';
import { MeusDesviosLookupService } from './meus-desvios.utils';

const desvio = (id: string, status = 'ABERTO') => ({
  id,
  idInspecao: 'i1',
  dataInspecao: '2026-10-01T10:00:00Z',
  processo: { id: 'p1', nome: 'MANUTENÇÃO' },
  baseOperacional: { id: 'b1', nome: 'JUAZEIRO' },
  equipe: { id: 'q1', codigo: 'EQ-01' },
  idPergunta: 'x',
  pergunta: `Pergunta ${id}`,
  gravidade: 'ALTA',
  descricao: null,
  acaoCorretiva: null,
  prazo: null,
  status,
  dataResolucao: null,
  vencido: false,
  anexos: []
});

describe('MeusDesviosComponent', () => {
  let sst: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = (url = '/lista') =>
    abrirListagem(MeusDesviosComponent, 'lista', url, [
      { provide: SstService, useValue: sst },
      { provide: RhService, useValue: { listarEquipes: vi.fn(() => paginaTeste([])) } },
      { provide: AuthService, useValue: { currentUser: () => ({ permissoes: ['sst:inspecao:write'] }) } },
      { provide: CentrosCustoLookupService, useValue: { listar: () => of([]) } },
      { provide: MeusDesviosLookupService, useValue: { carregar: () => of({ processos: { content: [] }, bases: { content: [{ id: 'b1', nome: 'JUAZEIRO' }] } }) } }
    ]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    sst = {
      listarMeusDesvios: vi.fn(() => paginaTeste([desvio('d1'), desvio('d2')])),
      tratarMeuDesvio: vi.fn(() => of({ ...desvio('d1'), status: 'RESOLVIDO' })),
      tratarMeusDesviosEmMassa: vi.fn(() => of({ totalAtualizados: 2 }))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre nos desvios em aberto, pelo prazo', async () => {
    const { el } = await abrir();
    expect(sst['listarMeusDesvios']).toHaveBeenCalledWith(expect.objectContaining({ status: ['ABERTO', 'EM_TRATAMENTO'], sort: 'prazo,asc', page: 0, size: 25 }));
    expect(chipsDoPainel(el)).toEqual(['Status: Em aberto']);
    expect(el.querySelectorAll('tbody input[type="checkbox"]')).toHaveLength(2);
  });

  it('o atalho "Vencidos" troca o recorte e o link antigo ?vencidos=true é aceito', async () => {
    const { el } = await abrir('/lista?vencidos=true');
    await new Promise((r) => setTimeout(r));
    expect(sst['listarMeusDesvios']).toHaveBeenLastCalledWith(expect.objectContaining({ apenasVencidos: true }));
    const vencidos = Array.from(el.querySelectorAll('.ces-quick-filter')).find((b) => b.textContent!.includes('Abertos')) as HTMLButtonElement;
    vencidos.click();
    await new Promise((r) => setTimeout(r));
    expect(sst['listarMeusDesvios']).toHaveBeenLastCalledWith(expect.objectContaining({ status: ['ABERTO'], apenasVencidos: null }));
  });

  it('tratamento em lote envia os selecionados', async () => {
    const { componente } = await abrir();
    const tela = componente as unknown as {
      selecionados: { set(v: unknown[]): void };
      abrirTratamentoEmLote(): void;
      salvarTratamento(r: unknown): void;
    };
    tela.selecionados.set([desvio('d1'), desvio('d2')]);
    tela.abrirTratamentoEmLote();
    tela.salvarTratamento({ status: 'RESOLVIDO', acaoCorretiva: 'Trocado', prazo: null });
    expect(sst['tratarMeusDesviosEmMassa']).toHaveBeenCalledWith({ idsDesvios: ['d1', 'd2'], status: 'RESOLVIDO', acaoCorretiva: 'Trocado', prazo: null });
  });

  it('tratado que sai do recorte some da página; 404 vira estado de erro explicado', async () => {
    const { componente } = await abrir();
    const tela = componente as unknown as { abrirTratamento(d: unknown): void; salvarTratamento(r: unknown): void; linhas(): { id: string }[] };
    tela.abrirTratamento(desvio('d1'));
    tela.salvarTratamento({ status: 'RESOLVIDO' });
    expect(tela.linhas().map((l) => l.id)).toEqual(['d2']);

    TestBed.resetTestingModule();
    sst['listarMeusDesvios'] = vi.fn(() => throwError(() => ({ status: 404, error: { detail: 'Usuário sem colaborador vinculado.' } })));
    const { el } = await abrir();
    expect(el.textContent).toContain('Usuário sem colaborador vinculado.');
  });
});
