package com.nexo.app.modules.financeiro.application.dto;

import com.nexo.app.modules.financeiro.domain.FonteReferencia;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Origem das opções de um campo de referência.
 *
 * <p>Publicar o endpoint junto do campo permite ao frontend montar o combo sem
 * ter, em código, um mapa de "qual campo busca em qual serviço" — que quebraria
 * a cada campo novo criado em produção.</p>
 */
@Schema(description = "Cadastro que alimenta as opções de um campo de referência")
public record ReferenciaSchema(

        @Schema(description = "Identificador da fonte", example = "FORNECEDOR")
        FonteReferencia fonte,

        @Schema(description = "Endpoint a consumir para listar as opções",
                example = "/v1/financeiro/fornecedores")
        String endpointOpcoes,

        @Schema(description = "Nome amigável da fonte", example = "Fornecedores")
        String descricao
) {}
