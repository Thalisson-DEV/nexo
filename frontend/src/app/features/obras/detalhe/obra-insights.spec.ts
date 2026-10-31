import { describe, expect, it } from 'vitest';

import { ObraResponse, OrcamentoObraResponse } from '../../../core/models/obras.models';
import { ContextoInsightsObra, insightsDaObra } from './obra-insights';

const obra = (parcial: Partial<ObraResponse> = {}): ObraResponse =>
  ({
    id: 'o1',
    numero: '7000001',
    titulo: 'OBRA',
    dataInicio: '01/01/2027',
    dataPrevisaoFim: '30/06/2027',
    statusId: 's1',
    statusNome: 'PLANEJADA',
    statusConcluida: false,
    latitude: -9.4,
    longitude: -40.5,
    equipes: ['e1'],
    anexos: [{ id: 'a1' }],
    subprojetos: [],
    carteira: { idContrato: 'c1', classeTensao: 'MT_15' },
    ...parcial
  }) as ObraResponse;

const orcamento = (total: number, semAcao = 0): OrcamentoObraResponse => ({
  idObra: 'o1',
  importado: true,
  totalServicosImportados: total,
  itens: Array.from({ length: 2 }, (_, i) => ({ id: `i${i}`, codigo: `C${i}`, quantidade: 1, valorTotal: 1, idAcao: i < semAcao ? null : 'a' }))
});

const contexto = (parcial: Partial<ContextoInsightsObra> = {}): ContextoInsightsObra => ({
  obra: obra(),
  hoje: new Date(2026, 9, 30),
  orcamento: orcamento(0),
  totalSolicitacoes: 1,
  totalProximas: 0,
  raioKm: 10,
  dataUltimoStatus: null,
  ...parcial
});

const titulos = (ctx: Partial<ContextoInsightsObra>) => insightsDaObra(contexto(ctx)).map((i) => i.titulo);

describe('insightsDaObra', () => {
  it('obra em dia e completa não gera alerta', () => {
    expect(insightsDaObra(contexto()).filter((i) => i.tom === 'danger' || i.tom === 'warning')).toEqual([]);
  });

  it('aponta atraso e prazo curto pelo limite mais próximo', () => {
    expect(titulos({ hoje: new Date(2027, 6, 10) })).toContain('10 dias de atraso');
    expect(titulos({ hoje: new Date(2027, 5, 20) })).toContain('Prazo vence em 10 dias');
    const regulado = obra({ carteira: { idContrato: 'c1', classeTensao: 'MT_15', prazoRegulado: '15/06/2027' } });
    expect(titulos({ obra: regulado, hoje: new Date(2027, 5, 10) })).toContain('Prazo vence em 5 dias');
  });

  it('avisa quando a previsão passa do prazo regulado', () => {
    const o = obra({ carteira: { idContrato: 'c1', classeTensao: 'MT_15', prazoRegulado: '15/06/2027' } });
    const insight = insightsDaObra(contexto({ obra: o })).find((i) => i.titulo === 'Previsão depois do prazo regulado');
    expect(insight?.texto).toContain('15 dias');
    expect(insight?.acao?.tipo).toBe('cadastro');
  });

  it('cobra equipe e material só perto do início, e urgente sem equipe é grave', () => {
    const semEquipe = obra({ equipes: [] });
    expect(titulos({ obra: semEquipe })).not.toContain('Sem equipe alocada');
    expect(titulos({ obra: semEquipe, hoje: new Date(2026, 11, 25) })).toContain('Sem equipe alocada');
    expect(titulos({ totalSolicitacoes: 0, hoje: new Date(2026, 11, 25) })).toContain('Nenhum material solicitado');

    const urgente = obra({ equipes: [], carteira: { idContrato: 'c1', classeTensao: 'MT_15', prioridade: 'URGENTE' } });
    const [primeiro] = insightsDaObra(contexto({ obra: urgente }));
    expect(primeiro).toMatchObject({ tom: 'danger', titulo: 'Obra urgente sem equipe' });
  });

  it('dados ainda não carregados não geram insight', () => {
    const lista = titulos({ orcamento: null, totalSolicitacoes: null, totalProximas: null, hoje: new Date(2026, 11, 25) });
    expect(lista).not.toContain('Sem orçamento importado');
    expect(lista).not.toContain('Nenhum material solicitado');
  });

  it('compara o orçamento importado com a carteira e aponta serviços fora do contrato', () => {
    const o = obra({ carteira: { idContrato: 'c1', classeTensao: 'MT_15', valorServicoPrevisto: 90000 } });
    expect(titulos({ obra: o, orcamento: orcamento(4271) })).toContain('Orçamento cobre 5% do serviço previsto');
    expect(titulos({ obra: o, orcamento: orcamento(89000) })).toContain('Orçamento coerente com a carteira');
    expect(titulos({ orcamento: orcamento(10, 1) })).toContain('1 serviço fora do contrato');
  });

  it('confere material e serviço contra o valor de projeto', () => {
    const o = obra({ valorProjeto: 100000, carteira: { idContrato: 'c1', classeTensao: 'MT_15', valorMaterialPrevisto: 80000, valorServicoPrevisto: 70000 } });
    expect(titulos({ obra: o })).toContain('Previsto acima do valor de projeto');
  });

  it('pede os dados que travam as próximas etapas, com a ação que resolve', () => {
    const o = obra({ carteira: null, anexos: [], latitude: undefined, longitude: undefined });
    const lista = insightsDaObra(contexto({ obra: o }));
    expect(lista.find((i) => i.titulo === 'Classe de tensão não informada')?.acao?.tipo).toBe('carteira');
    expect(lista.find((i) => i.titulo === 'Nenhum anexo na obra')?.acao?.tipo).toBe('anexo');
    expect(lista.map((i) => i.titulo)).toContain('Sem coordenadas');
  });

  it('usa deslocamento, obras próximas e status parado', () => {
    const o = obra({ carteira: { idContrato: 'c1', classeTensao: 'MT_15', tempoDeslocamentoMin: 120, deslocamentoEstimado: true } });
    const lista = titulos({ obra: o, totalProximas: 2, dataUltimoStatus: '2026-08-01T10:00:00-03:00' });
    expect(lista).toContain('120 min de deslocamento da base');
    expect(lista).toContain('Deslocamento estimado');
    expect(lista).toContain('2 obras próximas');
    expect(lista.some((t) => t.startsWith('Status sem atualização'))).toBe(true);
  });

  it('obra concluída não cobra prazo, equipe nem dados de planejamento', () => {
    const o = obra({ statusConcluida: true, dataFimEfetiva: '10/07/2027', equipes: [], anexos: [], carteira: null });
    expect(insightsDaObra(contexto({ obra: o, hoje: new Date(2027, 7, 1), orcamento: null }))).toEqual([]);
  });

  it('ordena do mais grave ao positivo', () => {
    const o = obra({ equipes: [], anexos: [] });
    const tons = insightsDaObra(contexto({ obra: o, hoje: new Date(2027, 6, 10), totalProximas: 1 })).map((i) => i.tom);
    expect(tons[0]).toBe('danger');
    expect(tons.at(-1)).toBe('success');
  });
});
