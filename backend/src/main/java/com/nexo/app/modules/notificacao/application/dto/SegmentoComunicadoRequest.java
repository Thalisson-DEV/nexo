package com.nexo.app.modules.notificacao.application.dto;

import java.util.UUID;

import com.nexo.app.modules.notificacao.domain.TipoAlvoSegmento;

import jakarta.validation.constraints.NotNull;

/**
 * Uma fatia do público-alvo. Conforme o {@code tipoAlvo}, um dos dois campos de
 * destino é exigido — a validação exata fica no domínio, que é quem conhece a regra.
 */
public record SegmentoComunicadoRequest(

        @NotNull(message = "O tipo de público-alvo é obrigatório.")
        TipoAlvoSegmento tipoAlvo,

        /** Id do perfil de acesso ou do usuário, conforme o tipo. */
        UUID idAlvo,

        /** ADMINISTRATIVO ou CAMPO, quando o tipo é TIPO_USUARIO. */
        String valorAlvo
) {
}
