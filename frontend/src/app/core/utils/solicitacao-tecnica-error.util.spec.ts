import '@angular/compiler';
import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';
import {
  ehConflitoDeVersao,
  ehFalhaDeTransporte,
  mensagemErroSolicitacao,
  normalizeOcorrencia,
  validarOcorrencia,
} from './solicitacao-tecnica-error.util';

describe('solicitacao-tecnica-error.util', () => {
  it('normaliza NFKC, caixa alta, trim e espacos sem remover controles', () => {
    expect(normalizeOcorrencia('  oc   1042 ')).toBe('OC 1042');
    expect(normalizeOcorrencia('OC\x001042')).toContain('\x00');
  });

  it('rejeita caracteres de controle na ocorrencia', () => {
    const result = validarOcorrencia('OC\x001042', 'CORRETIVA');
    expect(result.valida).toBe(false);
    expect(result.erro).toContain('controle');
  });

  it('exige ocorrencia para corretiva e preventiva e omite para obra/LPT', () => {
    expect(validarOcorrencia('', 'CORRETIVA').valida).toBe(false);
    expect(validarOcorrencia('prev-01', 'PREVENTIVA').normalizada).toBe('PREV-01');
    expect(validarOcorrencia('qualquer', 'OBRA')).toMatchObject({ valida: true, normalizada: null });
    expect(validarOcorrencia('qualquer', 'LPT')).toMatchObject({ valida: true, normalizada: null });
  });

  it('reconhece somente conflito de versao explicito', () => {
    const versionConflict = new HttpErrorResponse({
      status: 409,
      error: { code: 'VERSAO_DESATUALIZADA', versaoAtual: 3 },
    });
    const unrelatedConflict = new HttpErrorResponse({
      status: 409,
      error: { code: 'CHAVE_IDEMPOTENCIA_REUTILIZADA' },
    });
    expect(ehConflitoDeVersao(versionConflict)).toBe(true);
    expect(ehConflitoDeVersao(unrelatedConflict)).toBe(false);
    expect(mensagemErroSolicitacao(versionConflict)).toContain('3');
  });

  it('identifica falhas de transporte', () => {
    expect(ehFalhaDeTransporte(new HttpErrorResponse({ status: 0 }))).toBe(true);
    expect(ehFalhaDeTransporte(new HttpErrorResponse({ status: 408 }))).toBe(true);
    expect(ehFalhaDeTransporte(new HttpErrorResponse({ status: 504 }))).toBe(true);
    expect(ehFalhaDeTransporte(new HttpErrorResponse({ status: 500 }))).toBe(false);
  });
});
