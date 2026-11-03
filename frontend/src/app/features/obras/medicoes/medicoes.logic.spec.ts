import { describe, expect, it } from 'vitest';
import {
  bloqueioDoRelatorio,
  cabeNoCiclo,
  codigoSituacao,
  competenciasRecentes,
  contarCodigos,
  noRecorte,
  noRecortePreFechamento,
  opcoesDistintas,
  pendenciasLevantamento,
  percentualMedido,
  rotuloCompetencia,
  situacaoLevantamento,
  situacaoNaPlanilha,
  somarGeradas,
  somarItens
} from './medicoes.logic';

describe('medicoes.logic', () => {
  it('situação do levantamento tem rótulo e ícone para cada caso', () => {
    expect(situacaoLevantamento({ concluido: true, prontoParaConcluir: false, pontos: 3 }).rotulo).toBe('Concluído');
    expect(situacaoLevantamento({ concluido: false, prontoParaConcluir: true, pontos: 3 }).rotulo).toBe('Pronto para concluir');
    expect(situacaoLevantamento({ concluido: false, prontoParaConcluir: false, pontos: 0 }).rotulo).toBe('Sem levantamento');
    expect(situacaoLevantamento({ concluido: false, prontoParaConcluir: false, pontos: 3 }).icone).toBe('pi-clock');
  });

  it('pendências do levantamento em uma frase', () => {
    expect(pendenciasLevantamento({ pontos: 5, pontosSemKit: 1, vaosSemKit: 2, concluido: false })).toBe('1 ponto sem kit e 2 vãos sem kit');
    expect(pendenciasLevantamento({ pontos: 0, pontosSemKit: 0, vaosSemKit: 0, concluido: false })).toBe('Nenhum ponto lançado no planejamento');
    expect(pendenciasLevantamento({ pontos: 5, pontosSemKit: 0, vaosSemKit: 0, concluido: false })).toBeNull();
    expect(pendenciasLevantamento({ pontos: 5, pontosSemKit: 2, vaosSemKit: 0, concluido: true })).toBeNull();
  });

  it('competência legível e lista das recentes', () => {
    expect(rotuloCompetencia('2026-11')).toBe('nov/2026');
    expect(rotuloCompetencia(null)).toBe('—');
    const lista = competenciasRecentes(new Date(2026, 0, 15), 3);
    expect(lista.map((c) => c.value)).toEqual(['2026-01', '2025-12', '2025-11']);
  });

  it('no ciclo entra o concluído até o fim da competência', () => {
    expect(cabeNoCiclo({ concluidoEm: '31/10/2026' }, '2026-10')).toBe(true);
    expect(cabeNoCiclo({ concluidoEm: '01/11/2026' }, '2026-10')).toBe(false);
    expect(cabeNoCiclo({ concluidoEm: null }, '2026-10')).toBe(true);
  });

  it('somas ignoram item sem valor e medição estornada', () => {
    expect(somarItens([{ valorTotal: 10 }, { valorTotal: null }, { valorTotal: 5.5 }])).toBe(15.5);
    expect(somarGeradas([{ status: 'GERADA', valorTotal: 100 }, { status: 'ESTORNADA', valorTotal: 50 }])).toBe(100);
  });

  it('percentual medido só com valor de projeto', () => {
    expect(percentualMedido(25, 200)).toBe(12.5);
    expect(percentualMedido(25, null)).toBeNull();
  });

  it('conta códigos colados sem repetir', () => {
    expect(contarCodigos('1053015\n1283327\r\n1053015; 7000001\t\n')).toBe(3);
    expect(contarCodigos('  ')).toBe(0);
  });
});

describe('filtros de medições', () => {
  const contexto = {
    idBaseOperacional: 'b1', baseOperacional: 'Base 1', idMunicipio: 'm1', municipio: 'Mun 1',
    idSupervisor: 's1', supervisor: 'Ana', statusObra: 'PLANEJADA', prioridade: 'ALTA'
  };
  const vazio = { bases: [], municipios: [], supervisores: [], status: [], prioridades: [] };
  const linha = {
    idObra: 'o1', numero: '1', titulo: 'T', contexto, tipoObra: 'OBRA', pontos: 4, vaos: 3, pontosSemVao: 1,
    pontosSemKit: 0, vaosSemKit: 2, semDesligamento: 0, comDesligamento: 4, naoDefinido: 0, levantadores: ['JOAO'],
    concluido: false, concluidoEm: null, concluidoPor: null, prontoParaConcluir: false
  };

  it('recorte da carteira: lista vazia não restringe', () => {
    expect(noRecorte(contexto, vazio)).toBe(true);
    expect(noRecorte(contexto, { ...vazio, bases: ['b1'], prioridades: ['ALTA', 'URGENTE'] })).toBe(true);
    expect(noRecorte(contexto, { ...vazio, supervisores: ['s2'] })).toBe(false);
    expect(noRecorte(null, { ...vazio, municipios: ['m1'] })).toBe(false);
  });

  it('pré-fechamento filtra por levantador, pendência e execução', () => {
    const r = { ...vazio, levantadores: [], pendencias: [], execucoes: [] };
    expect(noRecortePreFechamento(linha, r)).toBe(true);
    expect(noRecortePreFechamento(linha, { ...r, levantadores: ['MARIA'] })).toBe(false);
    expect(noRecortePreFechamento(linha, { ...r, pendencias: ['VAO_SEM_KIT'] })).toBe(true);
    expect(noRecortePreFechamento(linha, { ...r, pendencias: ['PONTO_SEM_KIT'] })).toBe(false);
    expect(noRecortePreFechamento(linha, { ...r, execucoes: ['SEM_DESLIGAMENTO', 'COM_DESLIGAMENTO'] })).toBe(true);
    expect(noRecortePreFechamento(linha, { ...r, execucoes: ['NAO_DEFINIDO'] })).toBe(false);
  });

  it('situação do levantamento como código', () => {
    expect(codigoSituacao({ concluido: false, prontoParaConcluir: false, pontos: 0 })).toBe('SEM_LEVANTAMENTO');
    expect(codigoSituacao({ concluido: false, prontoParaConcluir: true, pontos: 2 })).toBe('PRONTO');
  });

  it('opções distintas em ordem alfabética', () => {
    const itens = [{ id: 'b', nome: 'Zeta' }, { id: 'a', nome: 'Alfa' }, { id: 'b', nome: 'Zeta' }, { id: null, nome: 'x' }];
    expect(opcoesDistintas(itens, (i) => i.id, (i) => i.nome)).toEqual([
      { value: 'a', label: 'Alfa' },
      { value: 'b', label: 'Zeta' }
    ]);
  });

  it('situação da obra na planilha: erro bloqueia, conferência avisa', () => {
    expect(situacaoNaPlanilha({ erros: ['a', 'b'], paraConferir: 1 })).toMatchObject({ rotulo: '2 linhas com erro', tom: 'danger' });
    expect(situacaoNaPlanilha({ erros: [], paraConferir: 1 })).toMatchObject({ rotulo: '1 lançamento a conferir', tom: 'warning' });
    expect(situacaoNaPlanilha({ erros: [], paraConferir: 0 }).rotulo).toBe('Pronta');
  });

  it('bloqueio do relatório explica o que falta', () => {
    const pronta = { numero: '7000001', erros: [] };
    expect(bloqueioDoRelatorio([pronta], 0)).toBeNull();
    expect(bloqueioDoRelatorio([], 0)).toBe('Escolha ao menos uma obra.');
    expect(bloqueioDoRelatorio([pronta], 2)).toContain('sem o número da obra');
    expect(bloqueioDoRelatorio(Array.from({ length: 11 }, () => pronta), 0)).toContain('até 10 obras');
    expect(bloqueioDoRelatorio([pronta, { numero: '7000002', erros: ['x'] }], 0)).toBe('Corrija a planilha da obra 7000002 ou tire-a da seleção.');
  });
});
