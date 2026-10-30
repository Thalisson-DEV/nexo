package com.nexo.app.modules.frota.application.dto;

import java.util.UUID;

public record TipoImplementoResponse(UUID id, String nome, String descricao, boolean ativo) {
}
