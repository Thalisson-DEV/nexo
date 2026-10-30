package com.nexo.app.modules.financeiro.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Como a solicitação terminou: quem decidiu, quando e por quanto — ou, no caso de
 * desistência, quem cancelou e por quê.
 *
 * <p>Vem preenchido apenas nas partes que já aconteceram; enquanto a solicitação
 * aguarda decisão, todos os campos são nulos.</p>
 */
@Schema(description = "Desfecho da solicitação")
public record DesfechoResponse(

        @Schema(description = "Usuário que aprovou ou rejeitou")
        UUID usuarioDecisaoId,

        @Schema(description = "Login do usuário que decidiu, já resolvido")
        String usuarioDecisao,

        OffsetDateTime dataDecisao,

        @Schema(description = "Valor efetivamente autorizado")
        BigDecimal valorAprovado,

        String observacaoDecisao,

        String motivoRejeicao,

        UUID usuarioCancelamentoId,

        OffsetDateTime dataCancelamento,

        String motivoCancelamento
) {}
