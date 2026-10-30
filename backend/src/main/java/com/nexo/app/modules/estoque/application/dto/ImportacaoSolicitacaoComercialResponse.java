package com.nexo.app.modules.estoque.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Resposta do processo de prévia ou confirmação de importação de solicitações comerciais")
public record ImportacaoSolicitacaoComercialResponse(
        @Schema(description = "Lista de registros analisados")
        List<SolicitacaoComercialImportacaoDTO> registros,

        @Schema(description = "Total de registros processados", example = "10")
        int totalProcessados,

        @Schema(description = "Total de registros válidos para confirmação", example = "8")
        int totalSucesso,

        @Schema(description = "Total de registros com inconsistências", example = "2")
        int totalFalha,
        @Schema(description = "Etapa e resultado final da requisicao sincrona")
        ProcessamentoImportacao processamento
) {
    public ImportacaoSolicitacaoComercialResponse(
            List<SolicitacaoComercialImportacaoDTO> registros,
            int totalProcessados, int totalSucesso, int totalFalha) {
        this(registros, totalProcessados, totalSucesso, totalFalha, null);
    }
}
