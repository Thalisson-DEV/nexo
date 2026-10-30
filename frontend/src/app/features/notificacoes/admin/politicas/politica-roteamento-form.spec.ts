import { PoliticaRoteamentoResponse } from '../../../../core/models/notificacao.models';
import {
  montarPoliticaRequest,
  politicaParaFormulario,
  rotasIniciais,
  validarPolitica
} from './politica-roteamento-form';

describe('formulário de política de roteamento', () => {
  it('garante uma rota condicional e exibe uma única rota padrão sem condições', () => {
    const rotas = rotasIniciais('base');

    expect(rotas.filter((rota) => !rota.padrao)).toHaveLength(1);
    expect(rotas.filter((rota) => rota.padrao)).toHaveLength(1);
    expect(rotas.find((rota) => rota.padrao)?.condicoes).toEqual([]);
  });

  it('monta o payload completo sem ids de resposta ou estado auxiliar da interface', () => {
    const valor = politicaParaFormulario(resposta());
    valor.rotas.forEach((rota) => (rota.usuarioAtivo = true));

    expect(montarPoliticaRequest(valor)).toEqual({
      chaveGatilho: 'financeiro.solicitacao_submetida',
      nome: 'Diretoria financeira por base e frota',
      descricao: 'Descrição',
      rotas: [
        {
          ordem: 10,
          padrao: false,
          usuarioId: 'usuario-1',
          condicoes: [{ campo: 'frota', operador: 'IGUAL', valor: 'true' }]
        },
        { ordem: 100, padrao: true, usuarioId: 'usuario-2', condicoes: [] }
      ]
    });
  });

  it('rejeita ordem repetida, usuário não confirmado e rota condicional sem condições', () => {
    const rotas = rotasIniciais('base');
    rotas[0].ordem = 100;
    rotas[0].condicoes = [];
    rotas[1].usuarioId = 'usuario-2';
    rotas[1].usuarioAtivo = true;

    const erros = validarPolitica(
      {
        chaveGatilho: 'financeiro.solicitacao_submetida',
        nome: 'Política',
        descricao: '',
        rotas
      },
      ['base']
    );

    expect(erros['rotas[0].ordem']).toContain('não pode se repetir');
    expect(erros['rotas[0].usuarioId']).toContain('usuário ativo');
    expect(erros['rotas[0].condicoes']).toContain('ao menos uma condição');
  });

  it('rejeita variável fora do gatilho e valor não numérico para operador numérico', () => {
    const valor = politicaParaFormulario(resposta());
    valor.rotas.forEach((rota) => (rota.usuarioAtivo = true));
    valor.rotas[0].condicoes[0] = {
      ...valor.rotas[0].condicoes[0],
      campo: 'campoInexistente',
      operador: 'MAIOR_QUE',
      valor: 'dez'
    };

    const erros = validarPolitica(valor, ['frota']);

    expect(erros['rotas[0].condicoes[0].campo']).toContain('declarada pelo gatilho');
    expect(erros['rotas[0].condicoes[0].valor']).toContain('exige um número');
  });
});

function resposta(): PoliticaRoteamentoResponse {
  return {
    id: 'politica-1',
    chaveGatilho: 'financeiro.solicitacao_submetida',
    nome: 'Diretoria financeira por base e frota',
    descricao: 'Descrição',
    ativo: true,
    dataCriacao: '2026-08-26T10:00:00Z',
    rotas: [
      {
        id: 'rota-1',
        ordem: 10,
        padrao: false,
        usuarioId: 'usuario-1',
        condicoes: [
          {
            id: 'condicao-1',
            campo: 'frota',
            operador: 'IGUAL',
            operadorRotulo: 'é igual a',
            valor: 'true'
          }
        ]
      },
      {
        id: 'rota-2',
        ordem: 100,
        padrao: true,
        usuarioId: 'usuario-2',
        condicoes: []
      }
    ]
  };
}
