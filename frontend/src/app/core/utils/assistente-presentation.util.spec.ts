import { AssistenteEvidencia } from '../models/assistente.models';
import {
  formatAssistenteFieldName,
  formatAssistenteSource,
  formatAssistenteValue,
  getEvidencePeriod,
  getEvidenceQuerySummary,
  getEvidenceRows
} from './assistente-presentation.util';

describe('assistente presentation utils', () => {
  const evidence: AssistenteEvidencia = {
    fonte: 'sst_desvios',
    versao: '1',
    timezone: 'America/Sao_Paulo',
    amostra: 82,
    truncada: false,
    calculadaEm: '2026-08-02T10:00:02-03:00',
    consultaEfetiva: {
      dimensoes: ['equipe'],
      medidas: ['quantidade'],
      agruparPor: ['equipe'],
      limite: 10,
      filtros: [
        {
          campo: 'dataInspecao',
          operador: 'BETWEEN',
          valores: ['2026-04-01', '2026-06-30']
        }
      ]
    },
    dados: Array.from({ length: 7 }, (_, index) => ({ equipe: `Equipe ${index}`, quantidade: index }))
  };

  it('apresenta o período executado em pt-BR', () => {
    expect(getEvidencePeriod(evidence)).toBe('01/04/2026 a 30/06/2026');
  });

  it('resume a consulta sem expor JSON bruto', () => {
    expect(getEvidenceQuerySummary(evidence)).toEqual([
      'Dimensões: equipe',
      'Medidas: quantidade',
      'Agrupamento: equipe',
      'Limite: 10'
    ]);
  });

  it('limita o detalhamento visível das linhas retornadas', () => {
    expect(getEvidenceRows(evidence)).toHaveLength(5);
  });

  it('formata campos, percentuais e moeda para o Brasil', () => {
    expect(formatAssistenteFieldName('taxaSucesso')).toBe('Taxa Sucesso');
    expect(formatAssistenteValue(82.5, 'taxaSucesso')).toBe('82,5%');
    expect(formatAssistenteValue(1234.5, 'valorTotal')).toContain('1.234,50');
  });

  it('troca identificadores internos por nomes reconhecíveis', () => {
    expect(formatAssistenteSource('sst_desvios')).toBe('Desvios de segurança');
  });
});
