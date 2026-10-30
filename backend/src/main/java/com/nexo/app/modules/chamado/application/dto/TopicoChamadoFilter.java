package com.nexo.app.modules.chamado.application.dto;

import java.util.UUID;

public record TopicoChamadoFilter(
        String buscaGlobal,
        String codigo,
        UUID equipePadraoId,
        Boolean ativo
) {}
