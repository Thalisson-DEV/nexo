package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.modules.estoque.domain.CondicaoMaterial;
import java.util.UUID;

public record SaldoSerieFilter(
    UUID idEstoque,
    UUID idMaterial,
    UUID idEquipe,
    String numSerie,
    CondicaoMaterial condicao,
    Boolean ativo,
    String buscaGlobal
) {}
