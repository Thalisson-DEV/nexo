import { describe, it, expect } from 'vitest';
import { PerfilNormativoGovernanca } from '../../../core/models/governanca-sst.models';
import {
  fimMandatoAnual,
  montarCriacaoCorpo,
  perfilAplicaAoCadastro,
  periodoCipaAnual
} from './governanca-corpo-cadastro.utils';

const perfil: PerfilNormativoGovernanca = {
  id: 'perfil-ba',
  versao: 1,
  codigo: 'BA-CBMBA',
  versaoPerfil: 1,
  escopo: 'ESTADUAL',
  tipoCorpo: 'BRIGADA',
  jurisdicao: 'BA',
  edicao: '2026',
  inicioVigencia: '2026-01-01',
  confirmado: true,
  ativo: true
};

describe('cadastro de corpos de governança', () => {
  it('calcula e valida o mandato anual da CIPA inclusive em ano bissexto', () => {
    const inicio = new Date(2024, 1, 29);
    const fim = fimMandatoAnual(inicio);

    expect(fim.getFullYear()).toBe(2025);
    expect(fim.getMonth()).toBe(1);
    expect(fim.getDate()).toBe(28);
    expect(periodoCipaAnual(inicio, fim)).toBe(true);
    expect(periodoCipaAnual(inicio, new Date(2025, 1, 27))).toBe(false);
  });

  it('monta CIPA dimensionada com booleano explícito e campos configurados', () => {
    const request = montarCriacaoCorpo('CIPA', {
      nome: ' CIPA 2026/2027 ',
      idBaseOperacional: 'base-principal',
      idsBasesAbrangidas: ['base-principal', 'base-apoio'],
      regimeComposicao: 'CIPA_DIMENSIONADA',
      unidadeGovernanca: ' Estabelecimento Sul ',
      uf: 'RS',
      inicioPeriodo: new Date(2026, 7, 1),
      fimPeriodo: new Date(2027, 6, 31),
      grauRisco: 4,
      idPerfilNormativo: 'perfil-nr5',
      hipoteseRepresentanteNomeadoConfirmada: true
    });

    expect(request).toEqual({
      tipo: 'CIPA',
      regimeComposicao: 'CIPA_DIMENSIONADA',
      nome: 'CIPA 2026/2027',
      idBaseOperacional: 'base-principal',
      unidadeGovernanca: 'Estabelecimento Sul',
      uf: 'RS',
      inicioPeriodo: '2026-08-01',
      fimPeriodo: '2027-07-31',
      grauRisco: 4,
      idPerfilNormativo: 'perfil-nr5',
      hipoteseRepresentanteNomeadoConfirmada: false,
      idsBasesAbrangidas: ['base-apoio']
    });
  });

  it('envia regime nulo e confirmação falsa para Brigada', () => {
    const request = montarCriacaoCorpo('BRIGADA', {
      nome: 'Brigada Centro',
      idBaseOperacional: 'base-sp',
      idsBasesAbrangidas: [],
      regimeComposicao: 'REPRESENTANTE_NOMEADO',
      unidadeGovernanca: 'Edificação Centro',
      uf: 'SP',
      inicioPeriodo: new Date(2026, 0, 1),
      fimPeriodo: new Date(2026, 11, 31),
      grauRisco: 2,
      idPerfilNormativo: null,
      hipoteseRepresentanteNomeadoConfirmada: true
    });

    expect(request.regimeComposicao).toBeNull();
    expect(request.hipoteseRepresentanteNomeadoConfirmada).toBe(false);
  });

  it('aplica perfil estadual somente à UF e vigência correspondentes', () => {
    expect(perfilAplicaAoCadastro(perfil, 'BRIGADA', 'BA', new Date(2026, 7, 1))).toBe(true);
    expect(perfilAplicaAoCadastro(perfil, 'BRIGADA', 'SP', new Date(2026, 7, 1))).toBe(false);
    expect(perfilAplicaAoCadastro(perfil, 'BRIGADA', 'BA', new Date(2025, 7, 1))).toBe(false);
  });
});
