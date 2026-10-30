/**
 * Marcações de pessoas em texto (`@LOGIN`).
 *
 * Não existe endpoint para "marcar alguém": o servidor reconhece as marcações a partir do texto
 * enviado nos endpoints de interação que já existiam. O front só precisa do autocomplete para
 * inserir o **login** certo — e nunca um nome de exibição, que marcaria outra pessoa.
 */

/** Telas que aceitam `@` hoje. Novas origens entram aqui quando o backend passar a reconhecê-las. */
export type OrigemMencao = 'CHAMADO_INTERACAO';

export interface UsuarioMencionavel {
  id: string;
  /** Em MAIÚSCULAS — é exatamente o que se digita após o `@`. */
  login: string;
}

export interface MencaoUsuariosFiltros {
  /** Trecho digitado após o `@`; o próprio `@` é tolerado no início. */
  termo: string;
  origem?: OrigemMencao;
  /**
   * `true` em composer de comentário interno: a lista já vem sem quem não tem `chamado:manage`
   * e, portanto, não conseguiria abrir o texto.
   */
  visibilidadeRestrita?: boolean;
}
