import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { Condutor } from '../../../core/models/frota.models';
import { OPCOES_VAZIAS, abrirTelaFrota, chipsDoResumo, paginaDe } from '../shared/frota-teste';
import { FrotaCondutoresComponent } from './frota-condutores.component';

const condutor = {
  id: 'c1',
  idColaborador: 'col1',
  nome: 'MARIA SOUZA',
  funcao: 'ELETRICISTA',
  baseOperacional: 'BASE SALVADOR',
  cnhNumeroMascarado: '*******8901',
  cnhCategoria: 'D',
  cnhValidade: '2027-01-10',
  cnhPrimeiraHabilitacao: null,
  situacaoCnh: 'VIGENTE',
  diasParaVencerCnh: 90,
  idVeiculoAtual: null,
  placaAtual: null,
  conduzDesde: null,
  observacao: null,
  ativo: true
} as unknown as Condutor;

describe('FrotaCondutoresComponent', () => {
  let frota: Record<string, ReturnType<typeof vi.fn>>;
  let permissoes: string[];
  const abrir = (url = '/frota/condutores') => abrirTelaFrota(FrotaCondutoresComponent, 'frota/condutores', url, frota, permissoes);

  beforeEach(() => {
    TestBed.resetTestingModule();
    permissoes = ['frota:condutor:read'];
    frota = {
      opcoes: vi.fn(() => of(OPCOES_VAZIAS)),
      listarCondutores: vi.fn(() => paginaDe([condutor])),
      historicoVeiculosCondutor: vi.fn(() => paginaDe([]))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre nos condutores ativos (padrão) e ordena pela validade da CNH', async () => {
    const { el } = await abrir();
    expect(frota['listarCondutores']).toHaveBeenCalledWith(
      expect.objectContaining({ ativo: true, busca: null }),
      { page: 0, size: 25, sort: ['cnhValidade,asc'] }
    );
    expect(chipsDoResumo(el)).toEqual(['Cadastro: Somente ativos']);
    expect(el.textContent).toContain('MARIA SOUZA');
  });

  it('a busca (pode ter o número da CNH) vai ao backend mas nunca para a URL', async () => {
    const { componente, harness } = await abrir();
    const lista = (componente as unknown as { lista: { load(e: object): void } }).lista;
    lista.load({ page: 0, size: 25, sort: [], query: '12345678901', filters: [] });
    await harness.fixture.whenStable();
    expect(frota['listarCondutores']).toHaveBeenLastCalledWith(expect.objectContaining({ busca: '12345678901' }), expect.anything());
    expect(TestBed.inject(Router).url).not.toContain('12345678901');
  });

  it('a linha abre a CNH para quem edita e o histórico para quem só consulta', async () => {
    let tela = await abrir();
    (tela.el.querySelector('button[aria-label="Ver histórico: MARIA SOUZA"]') as HTMLButtonElement).click();
    expect(frota['historicoVeiculosCondutor']).toHaveBeenCalledWith('col1');

    TestBed.resetTestingModule();
    permissoes = ['frota:condutor:read', 'frota:condutor:write'];
    tela = await abrir();
    (tela.el.querySelector('button[aria-label="Atualizar CNH: MARIA SOUZA"]') as HTMLButtonElement).click();
    expect((tela.componente as unknown as { formularioAberto(): boolean }).formularioAberto()).toBe(true);
  });
});
