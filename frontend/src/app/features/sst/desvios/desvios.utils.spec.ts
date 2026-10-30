import '@angular/compiler';
import { describe, it, expect } from 'vitest';
import {
  filtrosDesviosVazios,
  formatarDataHoraIso,
  formatarDataLocal,
  montarDesvioFiltro
} from './desvios.utils';

describe('desvios.utils', () => {
  describe('montarDesvioFiltro', () => {
    it('seleção vazia não vira parâmetro', () => {
      const filtros = filtrosDesviosVazios();
      const resultado = montarDesvioFiltro(filtros, '', 0, 20, 'prazo,asc');
      
      expect(resultado.status).toBeNull();
      expect(resultado.gravidade).toBeNull();
      expect(resultado.apenasVencidos).toBeNull();
      expect(resultado.idInspetor).toBeNull();
      expect(resultado.dataInspecaoDe).toBeNull();
      expect(resultado.buscaGlobal).toBeNull();
    });

    it('datas de prazo saem como yyyy-MM-dd e as de inspeção como ISO completo', () => {
      const filtros = filtrosDesviosVazios();
      filtros.prazoDe = new Date('2026-08-01T12:00:00Z');
      filtros.dataInspecaoDe = new Date('2026-08-01T12:00:00Z');
      
      const resultado = montarDesvioFiltro(filtros, '', 0, 20, 'prazo,asc');
      
      expect(resultado.prazoDe).toBe(formatarDataLocal(filtros.prazoDe));
      expect(resultado.dataInspecaoDe).toBe(formatarDataHoraIso(filtros.dataInspecaoDe));
    });
  });
});
