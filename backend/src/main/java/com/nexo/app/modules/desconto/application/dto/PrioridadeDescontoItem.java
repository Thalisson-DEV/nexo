package com.nexo.app.modules.desconto.application.dto;

import com.nexo.app.modules.desconto.domain.SeveridadePrioridade;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * Uma pendência que o dashboard considera acionável hoje.
 *
 * <p>Cada prioridade carrega três coisas separadas de propósito: o que foi encontrado
 * ({@code titulo}), por que aquilo é um problema ({@code evidencia}) e o que fazer a
 * respeito ({@code acaoSugerida}). Um cartão que só mostra o número deixa a decisão
 * inteira para quem lê, e é assim que indicador vira enfeite.</p>
 *
 * <p>O {@code drilldown} é o recorte que reproduz exatamente aquele conjunto na
 * listagem de descontos — é o que permite sair do cartão direto para os registros,
 * sem refazer o filtro à mão.</p>
 */
public record PrioridadeDescontoItem(
        @Schema(description = "VALIDACAO_PARADA, BAIXA_PARADA, SEM_COMPROVACAO, "
                + "GLOSA_ELEVADA ou REINCIDENCIA")
        String tipo,
        SeveridadePrioridade severidade,
        String titulo,
        String evidencia,
        String acaoSugerida,

        @Schema(description = "IDs dos termos envolvidos, no máximo os 50 mais relevantes")
        List<String> amostraIds,

        @Schema(description = "Filtros que reproduzem este conjunto na listagem")
        Map<String, Object> drilldown
) {
}
