import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { Implemento } from '../../../core/models/frota.models';
import { OPCOES_VAZIAS, abrirTelaFrota, chipsDoResumo, paginaDe } from '../shared/frota-teste';
import { FrotaImplementosComponent } from './frota-implementos.component';

const implemento = {
  id: 'i1',
  identificacao: 'CESTO-01',
  idTipoImplemento: 't1',
  tipoImplemento: 'Cesto aéreo',
  fabricante: null,
  modelo: null,
  numeroSerie: null,
  anoFabricacao: null,
  idBaseOperacional: 'b1',
  baseOperacional: 'BASE SALVADOR',
  situacao: 'ATIVO',
  observacao: null,
  acoplamentoAtual: null,
  ativo: true
} as unknown as Implemento;

describe('FrotaImplementosComponent', () => {
  let frota: Record<string, ReturnType<typeof vi.fn>>;
  let permissoes: string[];
  const abrir = (url = '/frota/implementos') => abrirTelaFrota(FrotaImplementosComponent, 'frota/implementos', url, frota, permissoes);

  beforeEach(() => {
    TestBed.resetTestingModule();
    permissoes = ['frota:veiculo:read'];
    frota = {
      opcoes: vi.fn(() => of(OPCOES_VAZIAS)),
      tiposImplemento: vi.fn(() => paginaDe([{ id: 't1', nome: 'Cesto aéreo', ativo: true }])),
      listarVeiculos: vi.fn(() => paginaDe([])),
      listarImplementos: vi.fn(() => paginaDe([implemento])),
      historicoImplemento: vi.fn(() => of([]))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre já nos dados, sem filtros aplicados', async () => {
    const { el } = await abrir();
    expect(frota['listarImplementos']).toHaveBeenCalledWith(expect.objectContaining({ busca: null }), {
      page: 0,
      size: 25,
      sort: ['identificacao,asc']
    });
    expect(chipsDoResumo(el)).toEqual([]);
    expect(el.textContent).toContain('CESTO-01');
  });

  it('aplica filtro restaurado da URL e mostra o chip', async () => {
    const { el } = await abrir('/frota/implementos?acoplado=false');
    expect(frota['listarImplementos']).toHaveBeenLastCalledWith(expect.objectContaining({ acoplado: false }), expect.anything());
    expect(chipsDoResumo(el)).toEqual(['Acoplamento: Livres']);
  });

  it('a linha abre a edição e desativar fica no drawer (no máximo três ícones por linha)', async () => {
    permissoes = ['frota:veiculo:read', 'frota:veiculo:write'];
    const { el, harness } = await abrir();
    expect(el.querySelectorAll('tbody .ces-table-actions ces-icon-button').length).toBeLessThanOrEqual(3);
    (el.querySelector('button[aria-label="Editar: CESTO-01"]') as HTMLButtonElement).click();
    harness.detectChanges();
    await harness.fixture.whenStable();
    expect(document.body.textContent).toContain('Desativar implemento');
  });
});
