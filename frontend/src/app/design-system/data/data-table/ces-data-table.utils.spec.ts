import { describe, expect, it } from 'vitest';
import {
  CesDataTableColumn,
  filterLabel,
  filterRows,
  formatCell,
  nextSortRules,
  searchRows,
  sortRows,
  toCsv,
} from './ces-data-table.utils';

interface Linha {
  id: string;
  nome: string;
  realizado: number;
  pct: number | null;
}

const colunas: CesDataTableColumn<Linha>[] = [
  { key: 'nome', header: 'Inspetor' },
  { key: 'realizado', header: 'Realizado', type: 'integer' },
  { key: 'pct', header: '% atingido', type: 'percent' },
];

const linhas: Linha[] = [
  { id: '1', nome: 'Álvaro Souza', realizado: 10, pct: 50 },
  { id: '2', nome: 'beatriz Lima', realizado: 30, pct: null },
  { id: '3', nome: 'Carlos Dias', realizado: 10, pct: 120 },
];

describe('ces-data-table utils', () => {
  it('ordena por várias regras de forma estável e mantém ausência no fim em qualquer direção', () => {
    const porRealizadoDescEPct = sortRows(
      linhas,
      [
        { key: 'realizado', direction: 'desc' },
        { key: 'pct', direction: 'desc' },
      ],
      colunas,
    );
    expect(porRealizadoDescEPct.map((l) => l.id)).toEqual(['2', '3', '1']);

    expect(sortRows(linhas, [{ key: 'pct', direction: 'asc' }], colunas).map((l) => l.id)).toEqual(['1', '3', '2']);
    expect(sortRows(linhas, [{ key: 'pct', direction: 'desc' }], colunas).map((l) => l.id)).toEqual(['3', '1', '2']);
    expect(sortRows(linhas, [{ key: 'nome', direction: 'asc' }], colunas).map((l) => l.id)).toEqual(['1', '2', '3']);
  });

  it('busca sem acento e sem caixa exigindo todos os termos', () => {
    expect(searchRows(linhas, 'alvaro', colunas).map((l) => l.id)).toEqual(['1']);
    expect(searchRows(linhas, 'LIMA beat', colunas).map((l) => l.id)).toEqual(['2']);
    expect(searchRows(linhas, '  ', colunas)).toHaveLength(3);
    // Colunas numéricas não entram na busca por padrão.
    expect(searchRows(linhas, '30', colunas)).toHaveLength(0);
  });

  it('cicla asc → desc → sem e acumula critérios com Shift', () => {
    let regras = nextSortRules([], 'nome', false);
    expect(regras).toEqual([{ key: 'nome', direction: 'asc' }]);
    regras = nextSortRules(regras, 'realizado', true);
    expect(regras).toEqual([
      { key: 'nome', direction: 'asc' },
      { key: 'realizado', direction: 'asc' },
    ]);
    regras = nextSortRules(regras, 'nome', true);
    expect(regras[0]).toEqual({ key: 'nome', direction: 'desc' });
    regras = nextSortRules(regras, 'nome', true);
    expect(regras).toEqual([{ key: 'realizado', direction: 'asc' }]);
    expect(nextSortRules(regras, 'nome', false)).toEqual([{ key: 'nome', direction: 'asc' }]);
  });

  it('formata células em pt-BR', () => {
    expect(formatCell(1234.5, 'number')).toBe('1.234,5');
    expect(formatCell(92.46, 'percent')).toBe('92,5%');
    expect(formatCell(null, 'integer')).toBe('—');
    expect(formatCell('2026-09-14', 'date')).toBe('14/09/2026');
    expect(formatCell(10, 'currency')).toMatch(/R\$\s?10,00/);
  });

  it('gera CSV para Excel pt-BR com BOM, ponto e vírgula e vírgula decimal', () => {
    const csv = toCsv(
      [{ id: '9', nome: 'Silva; "Zé"', realizado: 3, pct: 87.5 }],
      colunas,
    );
    expect(csv.startsWith('﻿')).toBe(true);
    expect(csv.slice(1).split('\r\n')).toEqual(['Inspetor;Realizado;% atingido', '"Silva; ""Zé""";3;87,5']);
  });
  it('filtra por coluna combinando critérios e sem tratar ausência como zero', () => {
    expect(filterRows(linhas, [{ key: 'pct', operator: 'lt', value: 80 }], colunas).map((l) => l.id)).toEqual(['1']);
    expect(filterRows(linhas, [{ key: 'pct', operator: 'ne', value: 50 }], colunas).map((l) => l.id)).toEqual(['2', '3']);
    expect(
      filterRows(linhas, [{ key: 'realizado', operator: 'gte', value: 10 }, { key: 'nome', operator: 'contains', value: 'alvaro' }], colunas)
        .map((l) => l.id),
    ).toEqual(['1']);
    expect(filterRows(linhas, [{ key: 'inexistente', operator: 'eq', value: 1 }], colunas)).toHaveLength(3);
    expect(filterLabel({ key: 'pct', operator: 'lt', value: 80 }, colunas)).toBe('% atingido < 80%');
    expect(filterLabel({ key: 'nome', operator: 'contains', value: 'Ana' }, colunas)).toBe('Inspetor contém “Ana”');
  });
  it('ordena pelo valor e exibe/exporta/busca pelo texto de display', () => {
    const cols: CesDataTableColumn<{ g: number }>[] = [
      { key: 'g', header: 'Gravidade', type: 'custom', value: (r) => r.g, display: (r) => ['Baixa', 'Alta', 'Crítica'][r.g] },
    ];
    const rows = [{ g: 1 }, { g: 2 }, { g: 0 }];
    expect(sortRows(rows, [{ key: 'g', direction: 'desc' }], cols).map((r) => r.g)).toEqual([2, 1, 0]);
    expect(searchRows(rows, 'critica', cols)).toEqual([{ g: 2 }]);
    expect(toCsv(rows, cols).slice(1).split('\r\n')).toEqual(['Gravidade', 'Alta', 'Crítica', 'Baixa']);
  });
});
