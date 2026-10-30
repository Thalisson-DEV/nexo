package com.nexo.app.modules.financeiro.domain;

/**
 * Urgência de uma pendência apontada pelo dashboard. Não é atributo da
 * solicitação nem do documento: é a leitura que o dashboard faz do conjunto, e
 * existe para que a tela consiga ordenar e colorir as prioridades sem ter que
 * reinterpretar o texto delas.
 */
public enum SeveridadePrioridade {
    BAIXA,
    MEDIA,
    ALTA,
    CRITICA
}
