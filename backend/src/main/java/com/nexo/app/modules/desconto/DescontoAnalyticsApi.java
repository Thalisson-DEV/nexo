package com.nexo.app.modules.desconto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** API publica, somente leitura, para metricas deterministicas de descontos. */
public interface DescontoAnalyticsApi {

    RankingTaxaSucesso rankingTaxaSucessoPorTipoEquipamento(
            OffsetDateTime inicioInclusivo,
            OffsetDateTime fimExclusivo,
            int amostraMinima,
            int limite);

    record RankingTaxaSucesso(
            String metrica,
            String versao,
            OffsetDateTime inicioInclusivo,
            OffsetDateTime fimExclusivo,
            int amostraMinima,
            List<TaxaSucessoTipo> tipos,
            long totalResultadosFinais,
            OffsetDateTime calculadoEm) {
    }

    record TaxaSucessoTipo(
            UUID idTipoEquipamento,
            String tipoEquipamento,
            long baixados,
            long rejeitados,
            long resultadosFinais,
            BigDecimal taxaSucessoPercentual) {
    }
}
