package com.nexo.app.modules.execucao.domain;

/**
 * Tipo do lançamento de campo. Os três contam faturamento e meta pelos serviços.
 */
public enum TipoNota {
    /** A nota de sempre, com o número da OS ou do talão. */
    COMUM,
    /** Nota encontrada em campo, ainda sem número: o app gera um provisório e o oficial vem depois. */
    AVULSA,
    /** Ocorrência comercial, identificada pelo número da ocorrência (letras e números). */
    OCORRENCIA
}
