package com.nexo.app.modules.estoque;

import com.nexo.app.modules.rh.ProcessoNegocioResponse;

import java.util.UUID;

public record MaterialResponse(
    UUID id,
    String codigo,
    String descricao,
    String unidadeMedida,
    String suprMatrFlag,
    ProcessoNegocioResponse processo,
    AvaliacaoMaterialResponse avaliacaoTecnica,
    CentroInvestimentoResponse centroInvestimento,
    boolean detalhaSerie,
    boolean ativo
) {}
