import { describe, expect, it } from 'vitest';
import { PropostaPlanejamento } from '../../../core/models/obras-planejamento.models';
import { montarAceite, paraRevisao, pontoProximo } from './planejamento-revisao.component';

const kit = { idKit: 'k1', codigo: 'K1', descricao: 'Kit', textoLido: '9/400', marcado: true };

const proposta: PropostaPlanejamento = {
  avisos: [],
  pontos: [
    { referencia: 'D1', numero: '3', tipo: 'POSTE', pagina: 1, x: 0.1, y: 0.1, textosLidos: [], confianca: 0.9, conflito: false, kits: [kit] },
    { referencia: 'D2', numero: '4', tipo: 'POSTE', pagina: 1, x: 0.5, y: 0.5, textosLidos: [], confianca: 0.4, conflito: false, kits: [] },
    { referencia: 'D3', numero: '1', tipo: 'POSTE', pagina: 1, x: 0.8, y: 0.8, textosLidos: [], confianca: 0.9, conflito: true, kits: [] }
  ],
  vaos: [
    { referenciaA: 'D1', referenciaB: 'D2', metragem: 30, textosLidos: [], confianca: 0.9, kits: [kit] },
    { referenciaA: 'D1', referenciaB: '1', metragem: null, textosLidos: [], confianca: 0.9, kits: [kit] }
  ]
};

describe('revisão da leitura do Copiloto', () => {
  it('não deixa marcado o que tem conflito ou baixa confiança', () => {
    const { pontos } = paraRevisao(proposta, new Set(['1']));

    expect(pontos.map((p) => [p.numero, p.aceito])).toEqual([['3', true], ['4', false], ['1', false]]);
  });

  it('não sugere de novo um poste já marcado no mesmo lugar', () => {
    const marcados = [{ numero: '9', x: 0.105, y: 0.1 }];

    expect(paraRevisao(proposta, new Set(), marcados).pontos[0].aceito).toBe(false);
    expect(pontoProximo(0.1, 0.1, marcados)).toBe('9');
    expect(pontoProximo(0.5, 0.5, marcados)).toBeNull();
  });

  it('o aceite leva só o marcado, e vão só com os dois pontos resolvidos', () => {
    const { pontos, vaos } = paraRevisao(proposta, new Set(['1']));

    const aceite = montarAceite(pontos, vaos, new Set(['1']));

    expect(aceite.pontos.map((p) => p.referencia)).toEqual(['D1']);
    expect(aceite.pontos[0].idsKits).toEqual(['k1']);
    // D1—D2 fica de fora porque D2 não foi aceito; D1—1 liga ao ponto que já existe.
    expect(aceite.vaos.map((v) => v.referenciaB)).toEqual(['1']);
  });

  it('kit de vão sem metragem não vai no aceite', () => {
    const { pontos, vaos } = paraRevisao(proposta, new Set(['1']));

    expect(montarAceite(pontos, vaos, new Set(['1'])).vaos[0].idsKits).toEqual([]);
  });
});
