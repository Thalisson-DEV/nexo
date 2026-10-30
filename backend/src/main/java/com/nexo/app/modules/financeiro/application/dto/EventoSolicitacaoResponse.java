package com.nexo.app.modules.financeiro.application.dto;

import com.nexo.app.modules.financeiro.domain.StatusSolicitacao;
import com.nexo.app.modules.financeiro.domain.TipoEventoSolicitacao;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Ponto do histórico de uma solicitação financeira")
public record EventoSolicitacaoResponse(

        UUID id,

        TipoEventoSolicitacao tipo,

        @Schema(description = "Texto de exibição do tipo", example = "Decisão desfeita")
        String rotulo,

        StatusSolicitacao statusAnterior,

        StatusSolicitacao statusNovo,

        UUID usuarioId,

        @Schema(description = "Nome de quem fez a transição, já resolvido")
        String usuario,

        @Schema(description = "Valor solicitado (abertura e envio) ou autorizado (aprovação)")
        BigDecimal valor,

        @Schema(description = "Observação da aprovação ou motivo da rejeição, do cancelamento ou do desfazer")
        String texto,

        OffsetDateTime ocorridoEm
) {}
