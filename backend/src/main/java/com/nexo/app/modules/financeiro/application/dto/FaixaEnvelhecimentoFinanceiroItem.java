package com.nexo.app.modules.financeiro.application.dto;

import com.nexo.app.modules.financeiro.domain.TipoSolicitacao;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Uma faixa de idade do backlog da fila de decisão.
 *
 * <p>A idade é contada desde a entrada na <b>etapa atual</b>, não desde a abertura
 * da solicitação: um pedido submetido ontem depois de duas semanas em rascunho é
 * uma pendência nova na fila do aprovador, e cobrá-la como se tivesse 15 dias
 * apontaria o dedo para a pessoa errada.</p>
 *
 * <p>A quebra por {@link TipoSolicitacao} vem junto porque compra e pagamento
 * costumam ter donos diferentes na aprovação: uma faixa cheia diz que há atraso,
 * mas só a quebra diz de quem é a fila.</p>
 */
public record FaixaEnvelhecimentoFinanceiroItem(
        @Schema(description = "'0-7' | '8-15' | '16-30' | '31-60' | '>60'")
        String faixa,
        int idadeMinimaDias,

        @Schema(description = "null na última faixa, que é aberta")
        Integer idadeMaximaDias,

        long quantidade,
        BigDecimal valor,

        @Schema(description = "Quebra da faixa por domínio; sempre com as duas chaves")
        Map<TipoSolicitacao, Long> porTipo
) {
}
