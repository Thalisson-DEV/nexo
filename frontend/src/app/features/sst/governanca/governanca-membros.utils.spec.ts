import { describe, it, expect } from 'vitest';
import { CorpoGovernancaResumo } from '../../../core/models/governanca-sst.models';
import {
  FormularioMembroGovernanca,
  dataInicialSugerida,
  montarDadosMembro
} from './governanca-membros.utils';

const corpo: CorpoGovernancaResumo = {
  id: 'corpo-id',
  versao: 2,
  tipo: 'CIPA',
  regimeComposicao: 'CIPA_DIMENSIONADA',
  status: 'EM_PREPARACAO',
  nome: 'CIPA 2026',
  idBaseOperacional: 'base-id',
  baseNome: 'Base Salvador',
  unidadeGovernanca: 'Unidade',
  uf: 'BA',
  inicioPeriodo: '2026-08-01',
  fimPeriodo: '2027-07-31',
  quantidadeMembrosAtivos: 0,
  hipoteseRepresentanteNomeadoConfirmada: false,
  conformidadeNormativaValidada: false
};

function formulario(): FormularioMembroGovernanca {
  return {
    idColaborador: 'colaborador-id',
    papel: 'MEMBRO',
    representacao: 'EMPREGADOS',
    titularidade: 'TITULAR',
    cicloEleitoral: 'Eleição 2026/2027',
    classificacaoEleitoral: 1,
    atoDesignacao: '',
    reeleito: true,
    inicioParticipacao: new Date(2026, 7, 1, 12),
    turnoDisponivel: 'Administrativo'
  };
}

describe('governanca-membros.utils', () => {
  it('monta integrante eleito da CIPA com os campos eleitorais', () => {
    expect(montarDadosMembro('CIPA', corpo, formulario())).toEqual({
      idColaborador: 'colaborador-id',
      papel: 'MEMBRO',
      representacao: 'EMPREGADOS',
      titularidade: 'TITULAR',
      origem: 'ELEITO',
      cicloEleitoral: 'Eleição 2026/2027',
      classificacaoEleitoral: 1,
      atoDesignacao: null,
      reeleito: true,
      inicioParticipacao: '2026-08-01',
      turnoDisponivel: 'Administrativo'
    });
  });

  it('força o contrato normativo do representante nomeado', () => {
    const representante = montarDadosMembro(
      'CIPA',
      { ...corpo, regimeComposicao: 'REPRESENTANTE_NOMEADO' },
      { ...formulario(), papel: 'PRESIDENTE', atoDesignacao: 'Portaria 10/2026' }
    );

    expect(representante).toMatchObject({
      papel: 'REPRESENTANTE_NOMEADO',
      representacao: 'EMPREGADOS',
      titularidade: null,
      origem: 'DESIGNADO',
      atoDesignacao: 'Portaria 10/2026',
      cicloEleitoral: null,
      classificacaoEleitoral: null,
      reeleito: false
    });
  });

  it('remove todos os campos exclusivos da CIPA ao montar brigadista', () => {
    const brigadista = montarDadosMembro(
      'BRIGADA',
      { ...corpo, tipo: 'BRIGADA', regimeComposicao: null },
      { ...formulario(), papel: 'BRIGADISTA' }
    );

    expect(brigadista).toMatchObject({
      papel: 'BRIGADISTA',
      representacao: null,
      titularidade: null,
      origem: null,
      atoDesignacao: null,
      cicloEleitoral: null,
      classificacaoEleitoral: null,
      reeleito: false
    });
  });

  it('mantém a data sugerida dentro da vigência da formação', () => {
    expect(dataInicialSugerida(corpo, new Date(2026, 6, 1, 12))).toEqual(new Date(2026, 7, 1, 12));
    expect(dataInicialSugerida(corpo, new Date(2028, 0, 1, 12))).toEqual(new Date(2027, 6, 31, 12));
  });
});
