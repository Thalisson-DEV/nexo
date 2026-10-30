package com.nexo.app.modules.obras.application.dto;

public record AgenteResponsavelFilter(
        String buscaGlobal,
        String nome,
        String email,
        String empresaOrgao,
        Boolean ativo
) {
}
