import { describe, expect, it } from 'vitest';
import {
  formatarBytes,
  formatarDataParaApi,
  formatarMoeda,
  parseDataBrParaDate,
  sanitizarNumeroObra,
  sanitizarTituloObra,
  statusClass,
  statusIcon,
  statusTone
} from './obras-ui';

describe('Obras UI Helpers & Sanitization', () => {
  describe('sanitizarNumeroObra', () => {
    it('deve remover prefixos "B-" ou "B" seguidos de digitos', () => {
      expect(sanitizarNumeroObra('B-1228694')).toBe('1228694');
      expect(sanitizarNumeroObra('b-1228694')).toBe('1228694');
      expect(sanitizarNumeroObra('B1228694')).toBe('1228694');
      expect(sanitizarNumeroObra('b1228694')).toBe('1228694');
    });

    it('deve preservar zeros a esquerda', () => {
      expect(sanitizarNumeroObra('0994094')).toBe('0994094');
      expect(sanitizarNumeroObra('B-0012345')).toBe('0012345');
    });

    it('deve converter para maiusculas e limitar a 7 caracteres', () => {
      expect(sanitizarNumeroObra('sol1290')).toBe('SOL1290');
      expect(sanitizarNumeroObra('123456789')).toBe('1234567');
      expect(sanitizarNumeroObra('  abc-12345  ')).toBe('ABC-123');
    });

    it('deve retornar string vazia para valores nulos ou vazios', () => {
      expect(sanitizarNumeroObra(null)).toBe('');
      expect(sanitizarNumeroObra(undefined)).toBe('');
      expect(sanitizarNumeroObra('')).toBe('');
    });
  });

  describe('sanitizarTituloObra', () => {
    it('deve converter para caixa alta e reduzir espacos consecutivos', () => {
      expect(sanitizarTituloObra('  subestacao   central  reforma ')).toBe('SUBESTACAO CENTRAL REFORMA');
    });

    it('deve limitar a 300 caracteres', () => {
      const longo = 'A'.repeat(350);
      expect(sanitizarTituloObra(longo).length).toBe(300);
    });
  });

  describe('statusTone, statusClass e statusIcon', () => {
    it('deve identificar status EM EXECUÇÃO', () => {
      expect(statusTone('EM EXECUÇÃO')).toBe('info');
      expect(statusClass('EM EXECUÇÃO')).toContain('blue');
      expect(statusIcon('EM EXECUÇÃO')).toContain('spinner');
    });

    it('deve identificar status CONCLUÍDA', () => {
      expect(statusTone('CONCLUÍDA')).toBe('success');
      expect(statusClass('CONCLUÍDA')).toContain('green');
      expect(statusIcon('CONCLUÍDA')).toContain('check-circle');
    });

    it('deve identificar status CANCELADA', () => {
      expect(statusTone('CANCELADA')).toBe('danger');
      expect(statusClass('CANCELADA')).toContain('red');
      expect(statusIcon('CANCELADA')).toContain('times-circle');
    });

    it('deve identificar status PARALISADA', () => {
      expect(statusTone('PARALISADA')).toBe('warning');
      expect(statusClass('PARALISADA')).toContain('amber');
      expect(statusIcon('PARALISADA')).toContain('pause-circle');
    });

    it('deve identificar status PLANEJADA', () => {
      expect(statusTone('PLANEJADA')).toBe('neutral');
      expect(statusClass('PLANEJADA')).toContain('slate');
      expect(statusIcon('PLANEJADA')).toContain('calendar');
    });
  });

  describe('Formatadores (Moeda, Bytes, Datas)', () => {
    it('deve formatar moeda corretamente', () => {
      expect(formatarMoeda(185000)).toContain('185.000,00');
      expect(formatarMoeda(0)).toContain('0,00');
      expect(formatarMoeda(null)).toBe('R$ 0,00');
    });

    it('deve formatar bytes corretamente', () => {
      expect(formatarBytes(1024)).toBe('1 KB');
      expect(formatarBytes(4528190)).toBe('4.32 MB');
      expect(formatarBytes(0)).toBe('0 B');
    });

    it('deve formatar data para formato de API dd/MM/yyyy', () => {
      const data = new Date(2026, 2, 1); // 1 de março de 2026
      expect(formatarDataParaApi(data)).toBe('01/03/2026');
      expect(formatarDataParaApi(null)).toBeUndefined();
    });

    it('deve fazer parse de data brasileira para Date', () => {
      const parsed = parseDataBrParaDate('01/03/2026');
      expect(parsed).not.toBeNull();
      expect(parsed?.getDate()).toBe(1);
      expect(parsed?.getMonth()).toBe(2); // Março = index 2
      expect(parsed?.getFullYear()).toBe(2026);
      expect(parseDataBrParaDate('invalido')).toBeNull();
    });
  });

  describe('CSV_TEMPLATE_OBRAS', () => {
    it('deve possuir layout oficial de 18 colunas incluindo Supervisor e Coordenador', async () => {
      const { CSV_TEMPLATE_OBRAS } = await import('./obras-ui');
      const headerLine = CSV_TEMPLATE_OBRAS.split('\n')[0];
      const colunas = headerLine.split(';');

      expect(colunas.length).toBe(18);
      expect(colunas).toContain('Supervisor');
      expect(colunas).toContain('Coordenador');
      expect(colunas[11]).toBe('Supervisor');
      expect(colunas[12]).toBe('Coordenador');
    });
  });
});
