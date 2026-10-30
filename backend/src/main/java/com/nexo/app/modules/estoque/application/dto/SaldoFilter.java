package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.modules.estoque.domain.CondicaoMaterial;
import java.math.BigDecimal;
import java.util.UUID;

public record SaldoFilter(
    UUID idEstoque,
    UUID idMaterial,
    UUID idEquipe,
    CondicaoMaterial condicao,
    BigDecimal quantidadeMinima,
    BigDecimal quantidadeMaxima,
    Boolean ativo,
    String buscaGlobal,
    String numSerie,
    Boolean negativo
) {}
