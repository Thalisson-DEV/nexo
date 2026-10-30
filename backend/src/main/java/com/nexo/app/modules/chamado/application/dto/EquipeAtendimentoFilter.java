package com.nexo.app.modules.chamado.application.dto;

public record EquipeAtendimentoFilter(
        String buscaGlobal,
        String codigo,
        Boolean ativo
) {}
