package com.nexo.app.modules.sst.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Indicador consolidado de cumprimento das metas de inspeção na data de referência.
 *
 * @param percentualColaboradoresNaMeta colaboradores com a meta atingida / colaboradores com meta
 * @param percentualExecucaoGlobal      soma do realizado / soma das metas
 */
public record AcompanhamentoMetasResponse(
        LocalDate dataReferencia,
        long totalColaboradores,
        long atingiram,
        long emAndamento,
        long naoAtingiram,
        long somaMetas,
        long somaRealizado,
        BigDecimal percentualColaboradoresNaMeta,
        BigDecimal percentualExecucaoGlobal,
        List<AcompanhamentoMetaItem> itens
) {
}
