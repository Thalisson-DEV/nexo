import { UsuarioResponse } from '../../../../core/models/iam.models';
import {
  CondicaoRoteamentoRequest,
  OperadorCondicao,
  PoliticaRoteamentoRequest,
  PoliticaRoteamentoResponse
} from '../../../../core/models/notificacao.models';
import { OPERADORES_NUMERICOS } from '../../notificacoes-ui';

let sequenciaLocal = 0;

export interface CondicaoRotaForm extends CondicaoRoteamentoRequest {
  localId: string;
  operadorRotulo?: string;
}

export interface RotaRoteamentoForm {
  localId: string;
  ordem: number;
  padrao: boolean;
  usuarioId: string | null;
  usuarioAtivo: boolean | null;
  usuarioBusca: string;
  usuarios: UsuarioResponse[];
  usuariosLoading: boolean;
  condicoes: CondicaoRotaForm[];
}

export interface PoliticaFormValue {
  chaveGatilho: string;
  nome: string;
  descricao: string;
  rotas: RotaRoteamentoForm[];
}

export type PoliticaFieldErrors = Record<string, string>;

function localId(prefixo: string): string {
  sequenciaLocal += 1;
  return `${prefixo}-${sequenciaLocal}`;
}

export function novaCondicao(campo = ''): CondicaoRotaForm {
  return { localId: localId('condicao'), campo, operador: 'IGUAL', valor: '' };
}

export function novaRotaCondicional(ordem: number, campo = ''): RotaRoteamentoForm {
  return {
    localId: localId('rota'),
    ordem,
    padrao: false,
    usuarioId: null,
    usuarioAtivo: null,
    usuarioBusca: '',
    usuarios: [],
    usuariosLoading: false,
    condicoes: [novaCondicao(campo)]
  };
}

export function novaRotaPadrao(ordem = 100): RotaRoteamentoForm {
  return {
    localId: localId('rota'),
    ordem,
    padrao: true,
    usuarioId: null,
    usuarioAtivo: null,
    usuarioBusca: '',
    usuarios: [],
    usuariosLoading: false,
    condicoes: []
  };
}

export function rotasIniciais(campo = ''): RotaRoteamentoForm[] {
  return [novaRotaCondicional(10, campo), novaRotaPadrao()];
}

export function politicaParaFormulario(politica: PoliticaRoteamentoResponse): PoliticaFormValue {
  return {
    chaveGatilho: politica.chaveGatilho,
    nome: politica.nome,
    descricao: politica.descricao ?? '',
    rotas: politica.rotas.map((rota) => ({
      localId: localId('rota'),
      ordem: rota.ordem,
      padrao: rota.padrao,
      usuarioId: rota.usuarioId,
      usuarioAtivo: null,
      usuarioBusca: '',
      usuarios: [],
      usuariosLoading: false,
      condicoes: rota.padrao
        ? []
        : rota.condicoes.map((condicao) => ({
            localId: localId('condicao'),
            campo: condicao.campo,
            operador: condicao.operador,
            operadorRotulo: condicao.operadorRotulo,
            valor: condicao.valor
          }))
    }))
  };
}

export function montarPoliticaRequest(valor: PoliticaFormValue): PoliticaRoteamentoRequest {
  return {
    chaveGatilho: valor.chaveGatilho,
    nome: valor.nome.trim(),
    descricao: valor.descricao.trim() || null,
    rotas: valor.rotas.map((rota) => ({
      ordem: rota.ordem,
      padrao: rota.padrao,
      usuarioId: rota.usuarioId ?? '',
      condicoes: rota.padrao
        ? []
        : rota.condicoes.map((condicao) => ({
            campo: condicao.campo,
            operador: condicao.operador,
            valor: condicao.valor.trim()
          }))
    }))
  };
}

export function validarPolitica(
  valor: PoliticaFormValue,
  variaveisPermitidas: readonly string[]
): PoliticaFieldErrors {
  const erros: PoliticaFieldErrors = {};
  if (!valor.chaveGatilho) erros['chaveGatilho'] = 'Selecione um gatilho por ocorrência.';
  if (!valor.nome.trim()) erros['nome'] = 'Informe o nome da política.';
  else if (valor.nome.trim().length > 120) erros['nome'] = 'Use no máximo 120 caracteres.';
  if (valor.descricao.length > 500) erros['descricao'] = 'Use no máximo 500 caracteres.';

  const padroes = valor.rotas.filter((rota) => rota.padrao);
  const condicionais = valor.rotas.filter((rota) => !rota.padrao);
  if (padroes.length !== 1) erros['rotas'] = 'A política precisa ter exatamente uma rota padrão.';
  if (condicionais.length === 0) erros['rotas'] = 'Adicione ao menos uma rota condicional.';

  const contagemOrdens = valor.rotas.reduce((contagem, rota) => {
    contagem.set(rota.ordem, (contagem.get(rota.ordem) ?? 0) + 1);
    return contagem;
  }, new Map<number, number>());
  valor.rotas.forEach((rota, indice) => {
    const caminho = `rotas[${indice}]`;
    if (!Number.isInteger(rota.ordem) || rota.ordem < 0) {
      erros[`${caminho}.ordem`] = 'Informe uma ordem inteira maior ou igual a zero.';
    } else if ((contagemOrdens.get(rota.ordem) ?? 0) > 1) {
      erros[`${caminho}.ordem`] = 'A ordem não pode se repetir.';
    }

    if (!rota.usuarioId) erros[`${caminho}.usuarioId`] = 'Selecione um usuário ativo.';
    else if (rota.usuarioAtivo !== true) {
      erros[`${caminho}.usuarioId`] = 'O usuário precisa estar ativo e confirmado pela API.';
    }

    if (rota.padrao && rota.condicoes.length > 0) {
      erros[`${caminho}.condicoes`] = 'O caso contrário não aceita condições.';
    }
    if (!rota.padrao && rota.condicoes.length === 0) {
      erros[`${caminho}.condicoes`] = 'Adicione ao menos uma condição nesta rota.';
    }

    rota.condicoes.forEach((condicao, indiceCondicao) => {
      const caminhoCondicao = `${caminho}.condicoes[${indiceCondicao}]`;
      if (!variaveisPermitidas.includes(condicao.campo)) {
        erros[`${caminhoCondicao}.campo`] = 'Escolha uma variável declarada pelo gatilho.';
      }
      if (!condicao.valor.trim()) {
        erros[`${caminhoCondicao}.valor`] = 'Informe o valor da condição.';
      } else if (
        OPERADORES_NUMERICOS.includes(condicao.operador as OperadorCondicao) &&
        !Number.isFinite(Number(condicao.valor.trim()))
      ) {
        erros[`${caminhoCondicao}.valor`] = 'Este operador exige um número.';
      }
    });
  });
  return erros;
}

export function rotuloUsuario(usuario: UsuarioResponse): string {
  const nome = usuario.colaborador?.nomeCompleto?.trim() || usuario.login;
  const complemento = usuario.email?.trim() || usuario.login;
  return complemento && complemento !== nome ? `${nome} · ${complemento}` : nome;
}
