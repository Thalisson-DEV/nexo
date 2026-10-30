package com.nexo.app.modules.frota.application.dto;

import com.nexo.app.modules.frota.domain.CategoriaCnh;

import java.util.UUID;

public record TipoVeiculoResponse(UUID id, String nome, String descricao, CategoriaCnh categoriaCnhMinima, boolean ativo) {
}
