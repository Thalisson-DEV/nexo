/**
 * Reconhecimento de marcações (`@LOGIN`) no texto — espelho do padrão aplicado pelo servidor.
 *
 * O backend não devolve a lista de marcações de cada texto: ele grava a mensagem exatamente como
 * foi escrita. Para destacar na tela, o front reaplica o mesmo padrão sobre o texto recebido.
 */

/**
 * `@` seguido de 3 a 9 caracteres `[A-Za-z0-9_]`.
 *
 * A âncora à esquerda é o que impede `suporte@fornecedor.com` de virar marcação; a da direita
 * evita casar só o começo de um login mais longo.
 *
 * Use sempre por meio de {@link novoPadraoMarcacao}: um literal com `/g` guarda `lastIndex` entre
 * chamadas e passaria a pular ocorrências.
 */
export const PADRAO_MARCACAO_FONTE = '(?<![A-Za-z0-9_@])@([A-Za-z0-9_]{3,9})(?![A-Za-z0-9_])';

export function novoPadraoMarcacao(): RegExp {
  return new RegExp(PADRAO_MARCACAO_FONTE, 'g');
}

/** Teto imposto pelo servidor: acima disso a mensagem inteira é recusada com 422. */
export const LIMITE_MARCACOES_POR_MENSAGEM = 10;

/** Logins distintos marcados no texto, em maiúsculas. */
export function extrairMarcacoes(texto: string | null | undefined): string[] {
  if (!texto) return [];
  const encontrados = [...texto.matchAll(novoPadraoMarcacao())].map((item) =>
    item[1].toUpperCase()
  );
  return [...new Set(encontrados)];
}

export function escaparHtml(texto: string): string {
  return texto
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/**
 * Devolve o texto com as marcações envoltas em `<span>`.
 *
 * O escape vem **antes** do destaque: o texto é digitado por usuários e nunca pode ser injetado
 * cru. Marcações não viram link — não há tela de perfil, e link morto é pior que texto destacado.
 *
 * Não dá para distinguir um `@` que resolveu de um erro de digitação (o servidor não informa isso
 * por interação); destacar todos é o comportamento aceito.
 */
export function destacarMarcacoes(
  texto: string | null | undefined,
  loginAtual?: string | null
): string {
  if (!texto) return '';
  const proprio = loginAtual?.trim().toUpperCase() ?? '';

  return escaparHtml(texto).replace(novoPadraoMarcacao(), (marcacao, login: string) => {
    const ehVoce = proprio.length > 0 && login.toUpperCase() === proprio;
    const classe = ehVoce ? 'marcacao marcacao-voce' : 'marcacao';
    return `<span class="${classe}">${marcacao}</span>`;
  });
}

export interface MarcacaoEmDigitacao {
  /** Trecho já digitado depois do `@`, sem o `@`. */
  termo: string;
  /** Posição do `@` no texto. */
  inicio: number;
  /** Posição logo após o último caractere do trecho (normalmente o cursor). */
  fim: number;
}

/** Tamanho máximo do trecho aceito pelo padrão — passou disso, não é mais uma marcação. */
const MAXIMO_CARACTERES_LOGIN = 9;

/**
 * Identifica se o cursor está dentro de um `@algo` em digitação.
 *
 * Aplica as mesmas âncoras do padrão gravado pelo servidor: um `@` colado em letra ou número à
 * esquerda (um e-mail, por exemplo) não abre o autocomplete.
 */
export function localizarMarcacaoEmDigitacao(
  texto: string,
  cursor: number
): MarcacaoEmDigitacao | null {
  const limite = Math.max(0, Math.min(cursor, texto.length));

  for (let posicao = limite - 1; posicao >= 0 && limite - posicao <= MAXIMO_CARACTERES_LOGIN + 1; posicao--) {
    const caractere = texto[posicao];

    if (caractere === '@') {
      const anterior = posicao > 0 ? texto[posicao - 1] : '';
      if (anterior && /[A-Za-z0-9_@]/.test(anterior)) return null;
      return { termo: texto.slice(posicao + 1, limite), inicio: posicao, fim: limite };
    }

    if (!/[A-Za-z0-9_]/.test(caractere)) return null;
  }

  return null;
}

export interface TextoComMarcacaoInserida {
  texto: string;
  /** Onde o cursor deve ficar depois da inserção. */
  cursor: number;
}

/** Troca o `@trecho` em digitação pelo login escolhido, deixando um espaço para continuar a frase. */
export function inserirMarcacao(
  texto: string,
  alvo: MarcacaoEmDigitacao,
  login: string
): TextoComMarcacaoInserida {
  const marcacao = `@${login.toUpperCase()}`;
  const sufixo = texto.slice(alvo.fim);
  const espaco = sufixo.startsWith(' ') ? '' : ' ';
  return {
    texto: `${texto.slice(0, alvo.inicio)}${marcacao}${espaco}${sufixo}`,
    cursor: alvo.inicio + marcacao.length + espaco.length
  };
}
