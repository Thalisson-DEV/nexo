import { convertToParamMap } from '@angular/router';
import { contextoDaUrl } from './copiloto-workspace.component';

describe('contexto da URL do Copiloto', () => {
  it('não cria contexto para uma conversa aberta pelo menu', () => {
    expect(contextoDaUrl(convertToParamMap({}))).toBeNull();
  });

  it('preserva origem, entidade e filtros dos atalhos sem transformar contexto em autorização', () => {
    expect(contextoDaUrl(convertToParamMap({
      origem: 'Detalhe da obra', rota: '/obras/42', tipo: 'obra', entidade: '42',
      rotulo: 'Obra 42', f_obra: '42', f_periodo: '2026-09', pergunta: 'Resuma a obra',
    }))).toEqual({
      pagina: 'Detalhe da obra', rota: '/obras/42', entidadeTipo: 'obra', entidadeId: '42',
      rotulo: 'Obra 42', filtros: { obra: '42', periodo: '2026-09' },
    });
  });
});
