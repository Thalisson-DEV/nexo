import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { RhService } from '../../../core/services/rh.service';
import { abrirListagem, paginaTeste } from '../../../shared/listagem/listagem-teste';
import { SaldosComponent } from './saldos.component';

const estoques = [
  { id: 'e1', nomeIdentificador: 'ALMOXARIFADO CENTRAL', ativo: true },
  { id: 'e2', nomeIdentificador: 'ESTOQUE EQ-01', idEquipe: 'q1', ativo: true }
];

describe('SaldosComponent', () => {
  let abrirAba: ReturnType<typeof vi.fn>;
  const abrir = (url = '/lista') =>
    abrirListagem(SaldosComponent, 'lista', url, [
      { provide: AlmoxarifadoService, useValue: { listarEstoquesAtivos: vi.fn(() => paginaTeste(estoques)) } },
      {
        provide: RhService,
        useValue: { listarEquipes: vi.fn(() => paginaTeste([{ id: 'q1', codigo: 'EQ-01' }])), buscarEquipePorId: vi.fn(() => of({ id: 'q1', codigo: 'EQ-01' })) }
      }
    ]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    abrirAba = vi.fn();
    vi.stubGlobal('open', abrirAba);
  });

  afterEach(() => vi.unstubAllGlobals());

  type Tela = { escolherEstoque(id: string): void; escolherEquipe(id: string): void; idEstoque(): string | null; idEquipe(): string | null; busca: { set(v: string): void }; consultar(): void };

  it('sem estoque não abre o relatório', async () => {
    const { componente } = await abrir();
    (componente as unknown as Tela).consultar();
    expect(abrirAba).not.toHaveBeenCalled();
  });

  it('estoque e equipe se preenchem um pelo outro e o relatório abre em nova aba', async () => {
    const { componente } = await abrir();
    const tela = componente as unknown as Tela;
    tela.escolherEstoque('e2');
    expect(tela.idEquipe()).toBe('q1');
    tela.escolherEquipe('q1');
    expect(tela.idEstoque()).toBe('e2');
    tela.busca.set('cabo');
    tela.consultar();
    expect(abrirAba).toHaveBeenCalledWith('/almoxarifado/saldos/relatorio?idEstoque=e2&idEquipe=q1&buscaGlobal=cabo', '_blank');
  });

  it('reabre preenchido a partir da URL', async () => {
    const { componente } = await abrir('/lista?estoque=e1&busca=cabo');
    const tela = componente as unknown as Tela;
    expect(tela.idEstoque()).toBe('e1');
  });
});
