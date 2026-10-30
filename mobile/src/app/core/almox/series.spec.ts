import { acumular, aprenderPadrao, extrairSerie, foraDoPadrao, gerarSequencia, normalizarSerie, separarLote } from './series';

describe('séries', () => {
  it('normaliza para maiúsculas e tira espaços e caracteres de controle', () => {
    expect(normalizarSerie('  ab12\t')).toBe('AB12');
    expect(normalizarSerie('x\u0007y')).toBe('XY');
  });

  it('separa um lote colado por linha, vírgula, ponto e vírgula e tabulação', () => {
    expect(separarLote('a1\nb2, c3;d4\t e5\r\n\n')).toEqual(['A1', 'B2', 'C3', 'D4', 'E5']);
  });

  it('acumula sem repetir e informa repetidas e recusadas', () => {
    const r = acumular(['A1'], ['a1', 'B2', 'b2', 'C3'], { validar: s => (s === 'C3' ? 'não está no estoque' : null) });
    expect(r.series).toEqual(['A1', 'B2']);
    expect(r.novas).toEqual(['B2']);
    expect(r.repetidas).toEqual(['A1', 'B2']);
    expect(r.recusadas).toEqual([{ serie: 'C3', motivo: 'não está no estoque' }]);
  });

  it('respeita o limite da quantidade', () => {
    const r = acumular([], ['A', 'B', 'C'], { limite: 2 });
    expect(r.series).toEqual(['A', 'B']);
    expect(r.recusadas[0]).toEqual({ serie: 'C', motivo: 'passa da quantidade' });
  });

  it('gera sequência mantendo prefixo e zeros à esquerda', () => {
    expect(gerarSequencia('abc0098', 'ABC0102').series).toEqual(['ABC0098', 'ABC0099', 'ABC0100', 'ABC0101', 'ABC0102']);
    expect(gerarSequencia('9', '11').series).toEqual(['09', '10', '11']);
  });

  it('lê a série (AI 21) de etiquetas GS1, com e sem parênteses', () => {
    expect(extrairSerie('(01)07891234567895(21)ab123(10)L1')).toBe('AB123');
    expect(extrairSerie(']C10107891234567895' + '21SN998877' + '\u001d' + '10LOTE1')).toBe('SN998877');
    expect(extrairSerie('010789123456789517261231' + '21XYZ1')).toBe('XYZ1');
  });

  it('lê a série de QR com campo e de links', () => {
    expect(extrairSerie('Modelo: M-200\nS/N: 4455-AB\nLote: 9')).toBe('4455-AB');
    expect(extrairSerie('{"pn":"X1","serial":"Q77"}')).toBe('Q77');
    expect(extrairSerie('Nº Série: 000123')).toBe('000123');
    expect(extrairSerie('https://fab.example/p?id=9&sn=med42')).toBe('MED42');
  });

  it('mantém o código comum como série, sem confundir com GS1', () => {
    expect(extrairSerie(' 21ABC0042 \r\n')).toBe('21ABC0042');
    expect(extrairSerie('ABC0042')).toBe('ABC0042');
    expect(extrairSerie(']E07891234567895')).toBe('7891234567895');
  });

  it('aprende o padrão do lote e segura a leitura que destoa', () => {
    expect(aprenderPadrao(['ABC0001', 'ABC0002'])).toBeNull();
    const padrao = aprenderPadrao(['ABC0001', 'ABC0002', 'ABC0107']);
    expect(padrao).toEqual({ minimo: 7, maximo: 7, prefixo: 'ABC', soDigitos: false });
    expect(foraDoPadrao('ABC0300', padrao)).toBeNull();
    // Sequência que vira a dezena não é suspeita.
    expect(foraDoPadrao('TR0010', aprenderPadrao(['TR0003', 'TR0004', 'TR0009']))).toBeNull();
    expect(foraDoPadrao('7891234567895', padrao)).toContain('13 caracteres');
    expect(foraDoPadrao('XYZ0001', padrao)).toContain('ABC');
    const numerico = aprenderPadrao(['100200', '100201', '100305']);
    expect(foraDoPadrao('10020A', numerico)).toContain('letras');
  });

  it('recusa sequências inválidas com mensagem clara', () => {
    expect(gerarSequencia('A1', 'B5').erro).toContain('começo');
    expect(gerarSequencia('A5', 'A1').erro).toContain('maior');
    expect(gerarSequencia('A', 'B').erro).toContain('número');
    expect(gerarSequencia('A0', 'A9999').erro).toContain('máximo');
  });
});
