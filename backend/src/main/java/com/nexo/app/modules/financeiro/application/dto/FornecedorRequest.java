package com.nexo.app.modules.financeiro.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dados de cadastro ou edição de um fornecedor.
 *
 * <p>Apenas o nome é exigido no cadastro para não travar o solicitante que
 * descobre um fornecedor novo no meio do preenchimento; razão social e documento
 * passam a ser obrigatórios na homologação pelo financeiro.</p>
 */
public record FornecedorRequest(

        @Schema(description = "Nome pelo qual o fornecedor é conhecido", example = "Auto Peças Silva")
        @NotBlank(message = "O nome do fornecedor é obrigatório.")
        @Size(max = 150, message = "O nome do fornecedor deve ter no máximo 150 caracteres.")
        String nomeFantasia,

        @Schema(description = "Razão social registrada", example = "SILVA COMERCIO DE AUTO PECAS LTDA")
        @Size(max = 200, message = "A razão social deve ter no máximo 200 caracteres.")
        String razaoSocial,

        @Schema(description = "CNPJ ou CPF, com ou sem máscara", example = "12.345.678/0001-90")
        @Size(max = 20, message = "O documento deve ter no máximo 20 caracteres.")
        String documento,

        @Schema(description = "🔒 Banco, agência, conta e/ou chave PIX. Armazenado cifrado.",
                example = "Banco 001 | AG 1234 | C/C 56789-0 | PIX 12345678000190")
        String dadosBancarios,

        @Schema(description = "Anotações internas sobre o fornecedor")
        String observacoes
) {}
