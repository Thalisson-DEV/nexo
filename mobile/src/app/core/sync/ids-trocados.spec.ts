import { idAtual, registrarIdTrocado } from './ids-trocados';

describe('ids trocados (temporário → servidor)', () => {
  beforeEach(() => localStorage.removeItem('ces_ids_trocados'));

  it('devolve o id do servidor para um id temporário conhecido', () => {
    registrarIdTrocado('tmp-1', 'srv-1');
    expect(idAtual('tmp-1')).toBe('srv-1');
    expect(idAtual('srv-1')).toBeNull();
    expect(idAtual('desconhecido')).toBeNull();
  });

  it('ignora troca para o mesmo id', () => {
    registrarIdTrocado('a', 'a');
    expect(idAtual('a')).toBeNull();
  });

  it('guarda só os 100 mais recentes', () => {
    for (let i = 0; i < 120; i++) registrarIdTrocado(`t${i}`, `s${i}`);
    expect(idAtual('t0')).toBeNull();
    expect(idAtual('t19')).toBeNull();
    expect(idAtual('t20')).toBe('s20');
    expect(idAtual('t119')).toBe('s119');
  });

  it('armazenamento corrompido não quebra', () => {
    localStorage.setItem('ces_ids_trocados', '{quebrado');
    expect(idAtual('x')).toBeNull();
    registrarIdTrocado('x', 'y');
    expect(idAtual('x')).toBe('y');
  });
});
