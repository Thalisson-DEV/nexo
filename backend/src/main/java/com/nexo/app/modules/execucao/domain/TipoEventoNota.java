package com.nexo.app.modules.execucao.domain;

/**
 * O que aconteceu com a nota em cada ponto da linha do tempo.
 */
public enum TipoEventoNota {
    /** A nota chegou ao servidor com o que a equipe lançou em campo. */
    LANCADA,
    /** Serviços ou materiais corrigidos depois da sincronização. */
    ALTERADA,
    /** O número oficial da nota avulsa foi informado (ou corrigido). */
    NUMERO_OFICIAL
}
