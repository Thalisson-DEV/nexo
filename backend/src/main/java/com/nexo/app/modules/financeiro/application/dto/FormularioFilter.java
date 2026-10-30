package com.nexo.app.modules.financeiro.application.dto;

import com.nexo.app.modules.financeiro.domain.TipoSolicitacao;
import io.swagger.v3.oas.annotations.media.Schema;

/** Filtros da listagem de variantes de formulário. */
public record FormularioFilter(

        @Schema(description = "Busca rápida por código, título ou descrição")
        String buscaGlobal,

        @Schema(description = "Domínio da variante")
        TipoSolicitacao tipoSolicitacao,

        @Schema(description = "Filtra por registro ativo (soft delete)")
        Boolean ativo
) {}
