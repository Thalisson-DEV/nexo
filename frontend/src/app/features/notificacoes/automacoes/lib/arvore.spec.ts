import { describe, expect, it } from 'vitest';

import { Definicao, FlowNode } from '../../../../core/models/automacao.models';
import {
  buscarNo, definicaoBranca, duplicarNo, idUnico, inserirNo, moverNo, removerNo, slug, todosIds, visivelPara
} from './arvore';
import { montarLayout } from './layout';

function no(id: string, extra: Partial<FlowNode> = {}): FlowNode {
  return { id, type: 'notify.people', params: {}, ...extra };
}

function fluxo(steps: FlowNode[]): Definicao {
  return { ...definicaoBranca(), steps };
}

describe('árvore do fluxo', () => {
  it('gera ids legíveis e únicos a partir do rótulo', () => {
    expect(slug('Avisar o RH da Base')).toBe('avisar_o_rh_da_base');
    expect(slug('123 passos')).toBe('p_123_passos');
    const usados = new Set(['avisar']);
    expect(idUnico('Avisar', usados)).toBe('avisar_2');
    expect(usados.has('avisar_2')).toBe(true);
  });

  it('insere, encontra e remove um passo dentro de uma condição', () => {
    const d = fluxo([no('cond', { type: 'control.condition', then: [], else: [] })]);
    const com = inserirNo(d, { parentId: 'cond', slot: 'then', index: 0 }, no('avisar'));
    expect(buscarNo(com, 'avisar')?.id).toBe('avisar');
    expect(com.steps[0].then?.map((n) => n.id)).toEqual(['avisar']);
    expect(removerNo(com, 'avisar').steps[0].then).toEqual([]);
  });

  it('não move um contêiner para dentro dele mesmo', () => {
    const d = fluxo([no('escopo', { type: 'control.scope', steps: [no('dentro')] })]);
    expect(moverNo(d, 'escopo', { parentId: 'escopo', slot: 'steps', index: 0 })).toBe(d);
  });

  it('move um passo para depois do seguinte na mesma lista', () => {
    const d = fluxo([no('a'), no('b'), no('c')]);
    const movido = moverNo(d, 'a', { parentId: null, slot: 'steps', index: 2 });
    expect(movido.steps.map((n) => n.id)).toEqual(['b', 'a', 'c']);
  });

  it('duplica com ids novos, inclusive dos filhos', () => {
    const d = fluxo([no('escopo', { type: 'control.scope', steps: [no('dentro')] })]);
    const { definicao, id } = duplicarNo(d, 'escopo');
    expect(id).not.toBe('escopo');
    expect([...todosIds(definicao)].filter((i) => i !== 'trigger')).toHaveLength(4);
  });

  it('só enxerga passos anteriores e o conteúdo de contêineres já fechados', () => {
    const d = fluxo([
      no('rota'),
      no('cond', { type: 'control.condition', then: [no('dentro_sim')], else: [no('dentro_nao')] }),
      no('depois')
    ]);
    expect(visivelPara(d, 'dentro_nao').passos).toEqual(['rota']);
    expect(visivelPara(d, 'depois').passos).toEqual(['rota', 'cond', 'dentro_sim', 'dentro_nao']);
  });
});

describe('layout do canvas', () => {
  const rotulos = { sim: 'Sim', nao: 'Não', senao: 'Senão' };

  it('posiciona gatilho, passos e o "Adicionar ação" ligados em sequência', () => {
    const layout = montarLayout(fluxo([no('a'), no('b')]), new Set(), rotulos);
    const ids = layout.itens.map((i) => i.id);
    expect(ids).toContain('trigger');
    expect(ids).toContain('fim');
    expect(layout.conexoes.some((c) => c.para === 'fim')).toBe(true);
    const a = layout.itens.find((i) => i.id === 'a')!;
    const b = layout.itens.find((i) => i.id === 'b')!;
    expect(b.y).toBeGreaterThan(a.y);
  });

  it('abre colunas lado a lado para Sim e Não', () => {
    const d = fluxo([no('cond', { type: 'control.condition', then: [no('s')], else: [no('n')] })]);
    const layout = montarLayout(d, new Set(), rotulos);
    const s = layout.itens.find((i) => i.id === 's')!;
    const n = layout.itens.find((i) => i.id === 'n')!;
    expect(s.y).toBe(n.y);
    expect(s.x).toBeLessThan(n.x);
  });
});
