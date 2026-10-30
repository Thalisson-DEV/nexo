package com.nexo.app.modules.sst.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Cartão de metas do dashboard de inspeções.
 *
 * @param metaPeriodo             meta esperada no período, rateada a partir das metas vigentes
 * @param percentualAtingido      realizado / meta do período
 * @param inspetoresNaMeta        inspetores que já cumpriram a própria meta do período
 * @param colaboradoresSemAcesso  colaboradores com meta mas sem usuário — não conseguem lançar inspeções
 */
public record ResumoMetaDashboard(
        @Schema(description = "Falso quando nenhuma meta está vigente no período; os demais campos ficam zerados")
        boolean possuiMeta,

        BigDecimal metaPeriodo,
        long realizadoPeriodo,
        BigDecimal percentualAtingido,
        BigDecimal saldoParaMeta,

        long colaboradoresComMeta,
        long inspetoresNaMeta,
        BigDecimal percentualInspetoresNaMeta,
        long colaboradoresSemAcesso
) {
}
