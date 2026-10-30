import { describe, expect, it } from 'vitest';
import { SolicitacaoComercialImportRow } from '../../../core/models/almoxarifado.models';
import { contarSolicitacoesComerciaisPrevistas } from './solicitacao-comercial-importacao-contagem.util';

describe('contarSolicitacoesComerciaisPrevistas', () => {
  it('agrupa itens pela origem, destino e data, ignorando o requisitante legado', () => {
    const linha = (linha: number, destino: string, requisitante: string): SolicitacaoComercialImportRow => ({
      linha,
      nomeEstoqueOrigem: 'ALMOX CENTRAL',
      nomeEstoqueDestino: destino,
      nomeRequisitante: requisitante,
      codigoMaterial: 'MAT-001',
      quantidade: 1,
      dataPrevista: '2099-12-31',
      status: 'AGUARDANDO_PROCESSAMENTO'
    });
    const comErro = { ...linha(5, 'EQ-03', 'MARIA'), status: 'COM_ERRO' as const };

    expect(contarSolicitacoesComerciaisPrevistas([
      linha(2, 'EQ-01', 'JOÃO'), linha(3, 'EQ-01', 'MARIA'), linha(4, 'EQ-02', 'JOÃO'), comErro
    ])).toBe(2);
  });
});
