package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.modules.estoque.domain.CondicaoMaterial;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(description = "Devolução técnica de materiais da obra para um almoxarifado físico")
public record DevolucaoTecnicaRequest(
    @NotNull(message = "A obra é obrigatória")
    @Schema(description = "Obra de onde o material sai", requiredMode = Schema.RequiredMode.REQUIRED)
    UUID idObra,

    @Schema(description = "Ocorrência (OC/OS), obrigatória nas obras corretivas e preventivas")
    String ocorrencia,

    @NotNull(message = "A equipe responsável pela devolução é obrigatória")
    @Schema(description = "Equipe que entrega o material", requiredMode = Schema.RequiredMode.REQUIRED)
    UUID idEquipe,

    @NotNull(message = "O estoque físico de destino é obrigatório")
    @Schema(description = "Estoque físico que recebe o material", requiredMode = Schema.RequiredMode.REQUIRED)
    UUID idEstoqueDestino,

    @NotEmpty(message = "Informe pelo menos um item para devolução")
    @Schema(description = "Itens devolvidos")
    List<@Valid ItemDevolucaoTecnicaRequest> itens
) {
    @Schema(description = "Item da devolução técnica")
    public record ItemDevolucaoTecnicaRequest(
        @NotNull(message = "O material é obrigatório")
        @Schema(description = "ID do material; não precisa estar no estoque da obra", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID idMaterial,

        @NotNull(message = "O tipo da devolução é obrigatório")
        @Schema(description = "DEVOLUCAO_COMUM (222) ou DEVOLUCAO_CONCRETO (922)", requiredMode = Schema.RequiredMode.REQUIRED)
        TipoSolicitacaoTecnica tipo,

        @NotNull(message = "A quantidade é obrigatória")
        @Positive(message = "A quantidade deve ser positiva")
        @Schema(description = "Quantidade devolvida", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal quantidade,

        @Schema(description = "Condição do material (NOVO ou SUCATA). Padrão: NOVO")
        CondicaoMaterial condicao,

        @Schema(description = "Número de série (obrigatório para materiais que controlam série)")
        String numSerie,

        @Schema(description = "Quando falso, o material é devolvido e baixado no destino na mesma operação. Padrão: true")
        Boolean guardarNoEstoque
    ) {
        public boolean deveGuardar() {
            return guardarNoEstoque == null || guardarNoEstoque;
        }
    }
}
