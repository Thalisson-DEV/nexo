import { describe, expect, it } from 'vitest';

import { ExecucaoComercialResponse } from '../../../core/models/execucao.models';
import { diferencaDaNota, materiaisDaNota, resumoDaDiferenca } from './alterar-nota.utils';

const nota = {
  id: 'e1',
  referenciaNota: '9293818293',
  acoes: [
    { id: 'ea1', acao: { id: 'a1', codigo: 'CL', nome: 'LIGAÇÃO NOVA' } },
    { id: 'ea2', acao: { id: 'a2', codigo: 'CT', nome: 'CORTE' } }
  ],
  materiais: [
    { id: 'em1', material: { id: 'cabo', codigo: 'C16', descricao: 'CABO 16MM', unidadeMedida: 'M' }, quantidade: 10, operacao: 'APLICADO', origem: 'EQUIPE' },
    { id: 'em2', material: { id: 'med', codigo: 'MED', descricao: 'MEDIDOR', unidadeMedida: 'UN' }, quantidade: 1, operacao: 'APLICADO', numSerie: 'S-1', origem: 'EQUIPE' }
  ]
} as unknown as ExecucaoComercialResponse;

describe('alterar nota', () => {
  it('sem mudança no editor, não há diferença', () => {
    const d = diferencaDaNota(nota, [
      { id: 'a1', codigo: 'CL', nome: 'LIGAÇÃO NOVA', nova: false },
      { id: 'a2', codigo: 'CT', nome: 'CORTE', nova: false }
    ], materiaisDaNota(nota));
    expect(resumoDaDiferenca(d)).toEqual([]);
  });

  it('mostra serviço que saiu, quantidade reduzida e série trocada', () => {
    const materiais = materiaisDaNota(nota)
      .map((m) => (m.idMaterial === 'cabo' ? { ...m, quantidade: 7 } : m))
      .filter((m) => m.numSerie !== 'S-1');
    materiais.push({ chave: 'n1', idMaterial: 'med', rotulo: 'MED · MEDIDOR', unidade: 'UN', quantidade: 1, operacao: 'APLICADO', numSerie: 'S-2', origem: 'EQUIPE', nova: true });

    const d = diferencaDaNota(nota, [{ id: 'a1', codigo: 'CL', nome: 'LIGAÇÃO NOVA', nova: false }], materiais);

    expect(d.acoesRemovidas).toEqual(['CORTE']);
    expect(resumoDaDiferenca(d)).toEqual([
      'Sai o serviço CORTE',
      'Entra MED · MEDIDOR (série S-2): 1 UN aplicado',
      'Sai C16 · CABO 16MM: 3 M aplicado',
      'Sai MED · MEDIDOR (série S-1): 1 UN aplicado'
    ]);
  });
});
