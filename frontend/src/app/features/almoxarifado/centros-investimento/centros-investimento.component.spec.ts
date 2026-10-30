import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ImportacaoService } from '../../../core/services/importacao.service';
import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../shared/listagem/listagem-teste';
import { CentrosInvestimentoComponent } from './centros-investimento.component';

const registro = { id: 'r1', codigo: 'VALOR TESTE', ativo: true };

describe('CentrosInvestimentoComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = (url = '/lista') =>
    abrirListagem(CentrosInvestimentoComponent, 'lista', url, [
      { provide: AlmoxarifadoService, useValue: servico },
      { provide: ImportacaoService, useValue: { baixarModeloCsv: vi.fn() } }
    ]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    servico = {
      listarCentrosInvestimento: vi.fn(() => paginaTeste([registro])),
      criarCentroInvestimento: vi.fn(() => of({ ...registro, id: 'novo' })),
      atualizarCentroInvestimento: vi.fn(() => of(registro)),
      ativarCentroInvestimento: vi.fn(() => of(null)),
      desativarCentroInvestimento: vi.fn(() => of(null))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre já nos dados, sem etapa de pesquisa', async () => {
    const { el } = await abrir();
    expect(servico['listarCentrosInvestimento']).toHaveBeenCalledWith({ ativo: null, buscaGlobal: undefined, page: 0, size: 25, sort: 'codigo,asc' });
    expect(el.textContent).toContain('VALOR TESTE');
    expect(chipsDoPainel(el)).toEqual([]);
  });

  it('restaura o filtro da URL e mostra o chip', async () => {
    const { el, componente } = await abrir('/lista?status=true&busca=val');
    expect(servico['listarCentrosInvestimento']).toHaveBeenLastCalledWith(expect.objectContaining({ ativo: true, buscaGlobal: 'val' }));
    expect(chipsDoPainel(el)).toEqual(['Status: Ativo']);
    (componente as unknown as { lista: { reset(): void } }).lista.reset();
    expect(servico['listarCentrosInvestimento']).toHaveBeenLastCalledWith(expect.objectContaining({ ativo: null }));
  });

  it('"n" abre o cadastro em drawer e salvar valida antes de enviar', async () => {
    const { componente } = await abrir();
    tecla('n');
    const tela = componente as unknown as { formularioAberto(): boolean; principal: { set(v: string): void }; salvar(): void };
    expect(tela.formularioAberto()).toBe(true);
    tela.salvar();
    expect(servico['criarCentroInvestimento']).not.toHaveBeenCalled();
    tela.principal.set('Valor novo');
    tela.salvar();
    expect(servico['criarCentroInvestimento']).toHaveBeenCalledWith(expect.objectContaining({ codigo: 'Valor novo' }));
  });
});
