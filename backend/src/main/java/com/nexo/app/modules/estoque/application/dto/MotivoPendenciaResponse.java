package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.modules.estoque.domain.MotivoPendencia;
import java.util.UUID;

public record MotivoPendenciaResponse(UUID id, String nome, String descricao, boolean ativo) {
    public static MotivoPendenciaResponse de(MotivoPendencia motivo) {
        return new MotivoPendenciaResponse(motivo.getId(), motivo.getNome(), motivo.getDescricao(), motivo.isAtivo());
    }
}
