package com.nexo.app.modules.estoque.application.dto;

import java.util.UUID;

public record EstoqueResponse(
    UUID id,
    String nomeIdentificador,
    TipoEstoqueResponse tipoEstoque,
    UUID idBaseOperacional,
    UUID idEquipe,
    UUID idObra,
    boolean ativo
) {}
