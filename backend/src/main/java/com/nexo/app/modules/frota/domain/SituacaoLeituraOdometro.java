package com.nexo.app.modules.frota.domain;

/**
 * Qualidade de uma leitura do odômetro.
 *
 * <p>Só leituras {@link #VALIDA} contam para o KM atual. {@link #INCONSISTENTE} fica
 * guardada para o gestor conferir; {@link #ANULADA} foi descartada com justificativa.</p>
 */
public enum SituacaoLeituraOdometro {
    VALIDA, INCONSISTENTE, ANULADA
}
