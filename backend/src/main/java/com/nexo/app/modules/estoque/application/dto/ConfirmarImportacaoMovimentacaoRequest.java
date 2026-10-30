package com.nexo.app.modules.estoque.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "Requisição para confirmação de importação de movimentações de estoque")
public record ConfirmarImportacaoMovimentacaoRequest(
        @Schema(description = "Lista de registros validados a serem executados")
        @NotEmpty(message = "A lista de registros não pode ser vazia.")
        List<MovimentacaoImportacaoDTO> registros
) {}

