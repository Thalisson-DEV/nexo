import { describe, expect, it } from 'vitest';

import { MeuDesvioResponse } from '../../../core/models/meu-desvio.models';
import {
  desvioAtendeFiltros,
  filtrosMeusDesviosVazios,
  montarMeuDesvioFiltro
} from './meus-desvios.utils';

const filtrosVazios = filtrosMeusDesviosVazios();

function desvio(parcial: Partial<MeuDesvioResponse>): MeuDesvioResponse {
  return {
    id: 'desvio-1',
    status: 'ABERTO',
    gravidade: 'ALTA',
    vencido: false,
    processo: null,
    baseOperacional: null,
    equipe: null,
    ...parcial
  } as MeuDesvioResponse;
}

describe('meus-desvios utils', () => {
  it('monta paginação zero-based e preserva a ordenação', () => {
    const filter = montarMeuDesvioFiltro(
      'ABERTO',
      filtrosVazios,
      '  equipamento  ',
      40,
      20,
      'prazo,asc'
    );

    expect(filter).toMatchObject({
      status: ['ABERTO'],
      buscaGlobal: 'equipamento',
      page: 2,
      size: 20,
      sort: 'prazo,asc'
    });
  });

  it('omite as dimensões sem seleção e envia as selecionadas como lista', () => {
    const filter = montarMeuDesvioFiltro(
      'ABERTO',
      { ...filtrosVazios, gravidade: ['ALTA', 'CRITICA'], idCentroCusto: ['centro-1'] },
      '',
      0,
      20,
      'prazo,asc'
    );

    expect(filter.gravidade).toEqual(['ALTA', 'CRITICA']);
    expect(filter.idCentroCusto).toEqual(['centro-1']);
    expect(filter.idProcesso).toBeNull();
    expect(filter.idBaseOperacional).toBeNull();
  });

  it('usa o vencido fornecido pelo backend ao aplicar o filtro', () => {
    expect(desvioAtendeFiltros(desvio({}), 'ABERTO', {
      ...filtrosVazios,
      apenasVencidos: true
    })).toBe(false);
  });

  it('aceita o item quando ele bate com um dos valores selecionados na dimensão', () => {
    const item = desvio({ gravidade: 'CRITICA' });

    expect(desvioAtendeFiltros(item, 'ABERTO', {
      ...filtrosVazios,
      gravidade: ['ALTA', 'CRITICA']
    })).toBe(true);
    expect(desvioAtendeFiltros(item, 'ABERTO', {
      ...filtrosVazios,
      gravidade: ['BAIXA', 'MEDIA']
    })).toBe(false);
  });

  it('não restringe nada quando a dimensão está sem seleção', () => {
    expect(desvioAtendeFiltros(desvio({}), 'ABERTO', filtrosVazios)).toBe(true);
  });

  it('permite consulta sem atalho de status, retornando status nulo se filtro vazio', () => {
    const filter = montarMeuDesvioFiltro(null, filtrosVazios, '', 0, 20, 'prazo,asc');
    expect(filter.status).toBeNull();
  });

  it('utiliza o status dos filtros do painel quando não há atalho ativo', () => {
    const filter = montarMeuDesvioFiltro(
      null,
      { ...filtrosVazios, status: ['EM_TRATAMENTO', 'RESOLVIDO'] },
      '',
      0,
      20,
      'prazo,asc'
    );
    expect(filter.status).toEqual(['EM_TRATAMENTO', 'RESOLVIDO']);
  });

  it('prioriza o atalho ativo caso selecionado sobre o filtro do painel', () => {
    const filter = montarMeuDesvioFiltro(
      'ABERTO',
      { ...filtrosVazios, status: ['RESOLVIDO'] },
      '',
      0,
      20,
      'prazo,asc'
    );
    expect(filter.status).toEqual(['ABERTO']);
  });

  it('aceita desvios de qualquer status quando atalho é nulo e filtros.status está vazio', () => {
    expect(desvioAtendeFiltros(desvio({ status: 'RESOLVIDO' }), null, filtrosVazios)).toBe(true);
    expect(desvioAtendeFiltros(desvio({ status: 'EM_TRATAMENTO' }), null, filtrosVazios)).toBe(true);
  });
});

