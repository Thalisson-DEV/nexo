import { describe, expect, it } from 'vitest';
import { FormularioSchema, SolicitacaoFinanceira } from '../../../core/models/financeiro.models';
import { diasAte, pontosDeAtencao } from './solicitacao-financeira-atencao';

const schema = {
  campos: [
    { chave: 'vencimento', campoCanonico: 'VENCIMENTO' },
    { chave: 'temNf', campoCanonico: 'TEM_NF' },
    { chave: 'justificativa', campoCanonico: 'JUSTIFICATIVA_SEM_NF' }
  ]
} as unknown as FormularioSchema;

const HOJE = new Date(2026, 9, 23);

function solicitacao(parcial: Partial<SolicitacaoFinanceira> = {}): SolicitacaoFinanceira {
  return {
    valor: 500,
    valores: {},
    documentos: [{ situacao: 'CONFERIDO' }],
    fornecedor: { id: 'f', nome: 'HC PNEUS', razaoSocial: null, cadastrado: true },
    ...parcial
  } as unknown as SolicitacaoFinanceira;
}

describe('pontosDeAtencao', () => {
  it('não aponta nada numa solicitação em ordem', () => {
    expect(pontosDeAtencao(solicitacao(), schema, HOJE)).toEqual([]);
  });

  it('aponta falta de documento e documento devolvido, este valendo também depois da decisão', () => {
    expect(pontosDeAtencao(solicitacao({ documentos: [] }), schema, HOJE)[0].titulo).toBe('Sem documentos anexados');

    const [devolvido] = pontosDeAtencao(
      solicitacao({ documentos: [{ situacao: 'COM_PENDENCIA' }] } as never),
      schema,
      HOJE
    );
    expect(devolvido.tom).toBe('danger');
    expect(devolvido.aposDecisao).toBe(true);
  });

  it('cobra o vencimento vencido e o próximo', () => {
    const vencido = pontosDeAtencao(solicitacao({ valores: { vencimento: { valor: '2026-10-20' } } }), schema, HOJE);
    const amanha = pontosDeAtencao(solicitacao({ valores: { vencimento: { valor: '2026-10-24' } } }), schema, HOJE);

    expect(vencido[0].titulo).toBe('Vencimento já passou');
    expect(amanha[0].titulo).toBe('Vence em 1 dia');
  });

  it('traz a justificativa da falta de nota e o fornecedor fora do cadastro', () => {
    const pontos = pontosDeAtencao(
      solicitacao({
        valores: { temNf: { valor: false }, justificativa: { valor: 'Prestador MEI' } },
        fornecedor: { id: null, nome: 'Oficina X', razaoSocial: null, cadastrado: false }
      }),
      schema,
      HOJE
    );

    expect(pontos.map((p) => p.titulo)).toEqual(['Fornecedor fora do cadastro', 'Sem nota fiscal']);
    expect(pontos[1].texto).toBe('Justificativa: Prestador MEI');
  });

  it('avisa quando o pagamento passa do autorizado na compra', () => {
    const [ponto] = pontosDeAtencao(
      solicitacao({ compraVinculada: { id: 'c', numero: 3, status: 'APROVADO', valorAprovado: 400 } }),
      schema,
      HOJE
    );

    expect(ponto.titulo).toBe('Acima do autorizado na compra');
    expect(ponto.texto).toContain('compra nº 3');
  });
});

describe('diasAte', () => {
  it('conta dias de calendário, negativo no passado', () => {
    expect(diasAte('2026-10-23', HOJE)).toBe(0);
    expect(diasAte('2026-10-25', HOJE)).toBe(2);
    expect(diasAte('2026-10-21', HOJE)).toBe(-2);
  });
});
