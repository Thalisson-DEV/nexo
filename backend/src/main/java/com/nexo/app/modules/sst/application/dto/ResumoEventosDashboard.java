package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.TipoEventoSst;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Os números de cabeçalho da acidentalidade, incluindo métricas do período e globais.
 *
 * <p>Eventos cancelados não entram em nenhum deles: um registro indevido que continuasse
 * contando faria a série mentir justamente onde ela é usada para cobrar resultado.</p>
 */
@Schema(description = "Indicadores consolidados do período, com comparação ao período anterior de mesmo tamanho")
public record ResumoEventosDashboard(

        long total,
        long totalAnterior,
        BigDecimal variacaoPercentual,

        @Schema(description = "Dias desde o último evento válido de todo o histórico. "
                + "Ignora período e demais filtros; nulo quando ainda não existe evento.")
        Long diasSemEventos,

        @Schema(description = "Quantidade por tipo de ocorrência, do incidente sem lesão ao óbito")
        Map<TipoEventoSst, Long> porTipo,

        @Schema(description = "Eventos de tipos que implicam lesão")
        long comLesao,
        long comLesaoAnterior,
        BigDecimal variacaoComLesao,

        @Schema(description = "Ocorrências sem lesão: incidentes e danos materiais")
        long semLesao,

        @Schema(description = "Soma dos dias perdidos declarados no período")
        long diasPerdidos,

        @Schema(description = "Eventos com CAT declarada")
        long comCat,

        @Schema(description = "Participação da CAT entre os eventos com lesão (%)")
        BigDecimal coberturaCat,

        @Schema(description = "Média de horas entre a ocorrência e a comunicação ao SESMT")
        BigDecimal mediaHorasComunicacao,

        @Schema(description = "Eventos comunicados fora do prazo legal do tipo")
        long comunicacoesAtrasadas,

        @Schema(description = "Participação das comunicações fora do prazo (%)")
        BigDecimal percentualAtrasadas,

        @Schema(description = "Eventos ainda abertos (registrados ou em tratativa)")
        long abertos,

        long concluidos,

        @Schema(description = "Desvios de segurança apontados em inspeção no mesmo período — a base da pirâmide")
        long desviosNoPeriodo,

        @Schema(description = "Quantos desvios para cada evento com lesão. Quanto maior, mais a prevenção "
                + "está encontrando o problema antes de alguém se machucar.")
        BigDecimal razaoDesviosPorLesao
) {
}
