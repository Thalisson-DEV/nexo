package com.nexo.app.modules.financeiro.application.dto;

import com.nexo.app.modules.financeiro.domain.TipoSolicitacao;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Criação de uma variante de formulário. O código é imutável após a criação —
 * é por ele que integrações e atalhos referenciam a variante.
 */
public record FormularioRequest(

        @Schema(description = "Domínio da variante", example = "COMPRA")
        @NotNull(message = "O tipo de solicitação é obrigatório.")
        TipoSolicitacao tipoSolicitacao,

        @Schema(description = "Identificador estável da variante", example = "FROTA")
        @NotBlank(message = "O código da variante é obrigatório.")
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,39}",
                message = "O código deve começar com letra e conter apenas letras, números e '_'.")
        String codigo,

        @Schema(description = "Título exibido ao solicitante", example = "Solicitação de Compra — Frota")
        @NotBlank(message = "O título é obrigatório.")
        @Size(max = 150, message = "O título deve ter no máximo 150 caracteres.")
        String titulo,

        @Schema(description = "Texto de apresentação do formulário")
        @Size(max = 500, message = "A descrição deve ter no máximo 500 caracteres.")
        String descricao,

        @Schema(description = "Marca esta variante como a sugerida para o tipo", example = "false")
        boolean padrao
) {}
