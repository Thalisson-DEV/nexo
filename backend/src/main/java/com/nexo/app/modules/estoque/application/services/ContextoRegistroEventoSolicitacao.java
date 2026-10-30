package com.nexo.app.modules.estoque.application.services;

import com.nexo.app.modules.estoque.domain.OrigemComandoTecnico;

import java.util.Objects;
import java.util.UUID;

public record ContextoRegistroEventoSolicitacao(
        UUID idAutor,
        String autorSnapshot,
        OrigemComandoTecnico origem,
        String justificativa
) {
    public ContextoRegistroEventoSolicitacao {
        if (autorSnapshot == null || autorSnapshot.isBlank()) {
            throw new IllegalArgumentException("O snapshot do autor e obrigatorio");
        }
        autorSnapshot = autorSnapshot.trim();
        origem = Objects.requireNonNullElse(origem, OrigemComandoTecnico.SYSTEM);
        justificativa = justificativa == null || justificativa.isBlank()
                ? null : justificativa.trim();
    }
}
