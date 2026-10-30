import { PoliticaRoteamentoResponse } from '../../core/models/notificacao.models';
import { politicasCompativeisComRegra } from './notificacoes-ui';

describe('políticas compatíveis com regra', () => {
  it('lista ativas do mesmo gatilho e mantém apenas a inativa já associada', () => {
    const politicas = [
      politica('ativa-mesmo-gatilho', true, 'gatilho.a'),
      politica('inativa-atual', false, 'gatilho.a'),
      politica('inativa-nao-selecionada', false, 'gatilho.a'),
      politica('ativa-outro-gatilho', true, 'gatilho.b')
    ];

    expect(
      politicasCompativeisComRegra(politicas, 'gatilho.a', new Set(['inativa-atual'])).map(
        (politica) => politica.id
      )
    ).toEqual(['ativa-mesmo-gatilho', 'inativa-atual']);
  });
});

function politica(id: string, ativo: boolean, chaveGatilho: string): PoliticaRoteamentoResponse {
  return {
    id,
    ativo,
    chaveGatilho,
    nome: id,
    descricao: null,
    rotas: [],
    dataCriacao: '2026-08-26T10:00:00Z'
  };
}
