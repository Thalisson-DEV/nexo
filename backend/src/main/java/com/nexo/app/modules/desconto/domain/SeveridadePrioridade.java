package com.nexo.app.modules.desconto.domain;

/**
 * Urgência de uma pendência apontada pelo dashboard. Não é um atributo do desconto:
 * é a leitura que o dashboard faz do conjunto, e existe para que a tela consiga
 * ordenar e colorir as prioridades sem ter que reinterpretar o texto delas.
 */
public enum SeveridadePrioridade {
    BAIXA,
    MEDIA,
    ALTA,
    CRITICA
}
