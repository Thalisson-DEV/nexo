import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Observable, Subject, of } from 'rxjs';
import { afterEach, describe, expect, it } from 'vitest';
import { CesListResponse, CesListState, cesFilter, cesListState } from './ces-list-state';

const situacoes = [
  { label: 'Vencidos e a vencer', value: 'A_VENCER' },
  { label: 'Vencido', value: 'VENCIDO' },
  { label: 'Em dia', value: 'EM_DIA' },
];

interface Doc {
  id: string;
}

const filtros = () => ({
  situacao: cesFilter.value<string | null>('A_VENCER', { label: 'Situação', options: () => situacoes }),
  idsBase: cesFilter.list<string>([], { label: 'Base', param: 'base', itemLabel: (id) => `Base ${id}` }),
  ativo: cesFilter.value<boolean | null>(true, { label: 'Cadastro', format: (v) => (v === null ? null : v ? 'Somente ativos' : 'Somente desativados') }),
  cpf: cesFilter.value<string | null>(null, { label: 'CPF', url: false }),
  periodo: cesFilter.range({ from: null, to: null }, { label: 'Período' }),
});

@Component({ standalone: true, template: '' })
class ListaComponent {
  readonly lista = cesListState({ storageKey: 'teste-docs', filters: filtros(), sort: [{ key: 'vencimento', direction: 'asc' }] });
  readonly pedidos: unknown[] = [];
  respostas = new Subject<CesListResponse<Doc>>();
  readonly resultado = this.lista.results((pedido) => {
    this.pedidos.push(pedido);
    return this.respostas as Observable<CesListResponse<Doc>>;
  });
}

async function abrir(url: string) {
  TestBed.configureTestingModule({ providers: [provideRouter([{ path: 'docs', component: ListaComponent }])] });
  const harness = await RouterTestingHarness.create();
  const componente = await harness.navigateByUrl(url, ListaComponent);
  return { harness, componente, lista: componente.lista as CesListState<ReturnType<typeof filtros>>, router: TestBed.inject(Router) };
}

describe('cesListState', () => {
  afterEach(() => localStorage.clear());

  it('começa nos padrões: contagem zero e chip do padrão não removível', async () => {
    const { lista } = await abrir('/docs');
    expect(lista.activeCount()).toBe(0);
    expect(lista.filtered()).toBe(false);
    expect(lista.summary()).toEqual([
      { key: 'situacao', label: 'Situação: Vencidos e a vencer', removable: false },
      { key: 'ativo', label: 'Cadastro: Somente ativos', removable: false },
    ]);
    expect(lista.pageSize()).toBe(25);
    expect(lista.request()).toEqual({
      filters: { situacao: 'A_VENCER', idsBase: [], ativo: true, cpf: null, periodo: { from: null, to: null } },
      page: 0,
      size: 25,
      sort: [{ key: 'vencimento', direction: 'asc' }],
      query: '',
    });
  });

  it('restaura filtros, busca, página, tamanho e ordenação da URL e ignora inválidos', async () => {
    const { lista } = await abrir('/docs?situacao=VENCIDO&base=b1,b2&ativo=talvez&busca=ABC&pagina=3&tamanho=50&ordem=placa,desc&periodo=2026-01-01~2026-01-31&desconhecido=1');
    expect(lista.applied().situacao).toBe('VENCIDO');
    expect(lista.applied().idsBase).toEqual(['b1', 'b2']);
    expect(lista.applied().ativo).toBe(true);
    expect(lista.applied().periodo).toEqual({ from: '2026-01-01', to: '2026-01-31' });
    expect(lista.query()).toBe('ABC');
    expect(lista.page()).toBe(2);
    expect(lista.pageSize()).toBe(50);
    expect(lista.sort()).toEqual([{ key: 'placa', direction: 'desc' }]);
    expect(lista.activeCount()).toBe(3);
    expect(lista.summary().map((chip) => chip.label)).toEqual([
      'Situação: Vencido',
      'Base: 2 selecionados',
      'Cadastro: Somente ativos',
      'Período: 01/01/2026 a 31/01/2026',
    ]);
  });

  it('separa rascunho de aplicado e grava na URL só o que difere do padrão', async () => {
    const { componente, lista, router, harness } = await abrir('/docs');
    lista.filters.idsBase.set(['b9']);
    lista.filters.cpf.set('12345678900');
    expect(lista.applied().idsBase).toEqual([]);

    lista.apply();
    await harness.fixture.whenStable();
    expect(lista.applied().idsBase).toEqual(['b9']);
    expect(componente.pedidos).toHaveLength(1);
    const url = router.url;
    expect(url).toBe('/docs?base=b9');
    expect(url).not.toContain('12345678900');
  });

  it('remover um chip e limpar voltam aos padrões e recarregam da primeira página', async () => {
    const { componente, lista } = await abrir('/docs?situacao=VENCIDO&base=b1&pagina=4');
    lista.remove('idsBase');
    expect(lista.applied().idsBase).toEqual([]);
    expect(lista.page()).toBe(0);
    lista.reset();
    expect(lista.applied().situacao).toBe('A_VENCER');
    expect(lista.activeCount()).toBe(0);
    expect(componente.pedidos).toHaveLength(2);
  });

  it('recebe o lazyLoad da tabela com a ordenação do backend', async () => {
    const { componente, lista } = await abrir('/docs');
    lista.load({ page: 2, size: 50, sort: [{ key: 'dataVencimento', direction: 'desc' }], query: 'crlv', filters: [] });
    expect(componente.pedidos[0]).toMatchObject({ page: 2, size: 50, query: 'crlv', sort: [{ key: 'dataVencimento', direction: 'desc' }] });
  });

  it('resposta antiga não sobrescreve a mais recente e erro vira mensagem do Problem Details', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter([{ path: 'docs', component: ListaComponent }])] });
    const harness = await RouterTestingHarness.create();
    const componente = await harness.navigateByUrl('/docs', ListaComponent);
    const respostas: Subject<CesListResponse<Doc>>[] = [];
    const lista = cesListStateFor(componente);
    const resultado = TestBed.runInInjectionContext(() =>
      lista.results<Doc>(() => {
        const resposta = new Subject<CesListResponse<Doc>>();
        respostas.push(resposta);
        return resposta;
      }),
    );
    lista.load({ page: 1, size: 25, sort: [], query: '', filters: [] });
    lista.load({ page: 2, size: 25, sort: [], query: '', filters: [] });
    expect(resultado.state()).toBe('loading');
    respostas[0].next({ content: [{ id: 'pagina-2' }], page: { totalElements: 60 } });
    respostas[1].next({ content: [{ id: 'pagina-3' }], page: { totalElements: 60 } });
    expect(resultado.rows()).toEqual([{ id: 'pagina-3' }]);
    expect(resultado.total()).toBe(60);
    expect(resultado.state()).toBe('ready');

    lista.reload();
    respostas[2].error({ error: { detail: 'Filtro de base inválido.' } });
    expect(resultado.state()).toBe('error');
    expect(resultado.error()).toBe('Filtro de base inválido.');
  });

  it('results imediato carrega listas simples sem tabela em modo servidor', async () => {
    const { componente } = await abrir('/docs');
    const lista = cesListStateFor(componente);
    const resultado = TestBed.runInInjectionContext(() => lista.results<Doc>(() => of([{ id: 'a' }, { id: 'b' }]), { immediate: true }));
    expect(resultado.rows()).toHaveLength(2);
    expect(resultado.total()).toBe(2);
  });
});

function cesListStateFor(componente: ListaComponent): CesListState<ReturnType<typeof filtros>> {
  return componente.lista as CesListState<ReturnType<typeof filtros>>;
}
