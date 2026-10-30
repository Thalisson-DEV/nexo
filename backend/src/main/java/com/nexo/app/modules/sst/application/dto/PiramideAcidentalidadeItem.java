package com.nexo.app.modules.sst.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Um degrau da pirâmide de acidentalidade (Bird), da fatalidade à base preventiva.
 *
 * <p>A base não é um evento: é o <b>desvio de segurança</b> apontado nas inspeções, que
 * vive no outro lado do módulo. É isso que torna a pirâmide honesta aqui — a empresa não
 * registra quase-acidente como ocorrência, ela registra a não-conformidade encontrada em
 * inspeção, e é essa a massa que sustenta a base.</p>
 *
 * <p>Os eventos são agrupados pela consequência (fatalidade, com afastamento, sem
 * afastamento e incidente), enquanto a base vem do agregado de desvios.</p>
 */
public record PiramideAcidentalidadeItem(

        @Schema(description = "Chave semântica de um dos cinco níveis da pirâmide")
        String chave,

        String rotulo,
        long total,

        @Schema(description = "Participação no total da pirâmide (eventos + desvios)")
        BigDecimal participacaoPercentual,

        @Schema(description = "Quantos deste degrau para cada evento com lesão")
        BigDecimal razaoPorEventoComLesao
) {

    public static final String CHAVE_FATALIDADE = "FATALIDADE";
    public static final String CHAVE_ACIDENTE_COM_AFASTAMENTO = "ACIDENTE_COM_AFASTAMENTO";
    public static final String CHAVE_ACIDENTE_SEM_AFASTAMENTO = "ACIDENTE_SEM_AFASTAMENTO";
    public static final String CHAVE_INCIDENTE = "INCIDENTE";
    public static final String CHAVE_DESVIO = "DESVIO_SEGURANCA";
}
