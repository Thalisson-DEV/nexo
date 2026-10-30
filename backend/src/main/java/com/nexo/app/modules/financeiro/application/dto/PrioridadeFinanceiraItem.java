package com.nexo.app.modules.financeiro.application.dto;

import com.nexo.app.modules.financeiro.domain.SeveridadePrioridade;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * Uma pendência que o dashboard considera acionável hoje.
 *
 * <p>Cada prioridade carrega três coisas separadas de propósito: o que foi
 * encontrado ({@code titulo}), por que aquilo é um problema ({@code evidencia}) e
 * o que fazer a respeito ({@code acaoSugerida}). Um cartão que só mostra o número
 * deixa a decisão inteira para quem lê, e é assim que indicador vira enfeite.</p>
 *
 * <p>O {@code drilldown} é o recorte que reproduz exatamente aquele conjunto na
 * listagem — de solicitações ou de documentos, conforme {@code destino} — sem
 * refazer o filtro à mão.</p>
 */
public record PrioridadeFinanceiraItem(
        @Schema(description = "APROVACAO_PARADA, RASCUNHO_ESQUECIDO, VENCIMENTO_PROXIMO, "
                + "VENCIDO_SEM_COMPROVANTE, DOCUMENTO_AGUARDANDO_CONFERENCIA, SEM_NOTA_FISCAL, "
                + "CORTE_ELEVADO, FORNECEDOR_NAO_HOMOLOGADO, CONCENTRACAO_FORNECEDOR ou "
                + "DOCUMENTO_DUPLICADO")
        String tipo,
        SeveridadePrioridade severidade,
        String titulo,
        String evidencia,
        String acaoSugerida,

        @Schema(description = "Listagem que o drilldown abre: SOLICITACOES ou DOCUMENTOS")
        String destino,

        @Schema(description = "IDs dos registros envolvidos, no máximo os 50 mais relevantes")
        List<String> amostraIds,

        @Schema(description = "Filtros que reproduzem este conjunto na listagem de destino")
        Map<String, Object> drilldown
) {
}
