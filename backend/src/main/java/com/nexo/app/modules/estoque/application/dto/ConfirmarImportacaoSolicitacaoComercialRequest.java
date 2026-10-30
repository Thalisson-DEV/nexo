package com.nexo.app.modules.estoque.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "Requisição para confirmação de importação de solicitações comerciais de material")
public record ConfirmarImportacaoSolicitacaoComercialRequest(
        @NotEmpty(message = "A lista de registros para importação não pode estar vazia")
        @Schema(description = "Lista de itens validados na prévia")
        List<SolicitacaoComercialImportacaoDTO> registros
) {}
