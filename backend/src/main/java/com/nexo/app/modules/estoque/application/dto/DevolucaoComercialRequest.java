package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.modules.estoque.domain.CondicaoMaterial;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(description = "Dados para devolução comercial de materiais do estoque de campo de uma equipe para um almoxarifado físico (Tipo Fixo 222)")
public record DevolucaoComercialRequest(
    @NotNull(message = "A equipe responsável pela devolução é obrigatória")
    @Schema(description = "ID da equipe que possui a custódia do material em seu estoque de campo", requiredMode = Schema.RequiredMode.REQUIRED)
    UUID idEquipe,

    @NotNull(message = "O estoque físico de destino é obrigatório")
    @Schema(description = "ID do estoque físico de destino que receberá o material devolvido", requiredMode = Schema.RequiredMode.REQUIRED)
    UUID idEstoqueDestino,

    @NotEmpty(message = "Informe pelo menos um item para devolução")
    @Schema(description = "Lista de itens devolvidos")
    List<@Valid ItemDevolucaoComercialRequest> itens,

    @Schema(description = "Observação complementar sobre a devolução")
    String observacao
) {
    @Schema(description = "Item da devolução comercial")
    public record ItemDevolucaoComercialRequest(
        @NotNull(message = "O material é obrigatório")
        @Schema(description = "ID do material", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID idMaterial,

        @NotNull(message = "A quantidade é obrigatória")
        @Positive(message = "A quantidade deve ser positiva")
        @Schema(description = "Quantidade devolvida", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal quantidade,

        @Schema(description = "Condição do material (NOVO ou SUCATA). Padrão: NOVO")
        CondicaoMaterial condicao,

        @Schema(description = "Número de série (obrigatório para materiais que controlam série)")
        String numSerie,

        @Schema(description = "Se o almoxarifado destino guarda o material. Quando falso, o material é devolvido e baixado (baixa avulsa) no destino na mesma operação. Padrão: true")
        Boolean guardarNoEstoque
    ) {
        public ItemDevolucaoComercialRequest(UUID idMaterial, BigDecimal quantidade, CondicaoMaterial condicao, String numSerie) {
            this(idMaterial, quantidade, condicao, numSerie, null);
        }

        public boolean deveGuardar() {
            return guardarNoEstoque == null || guardarNoEstoque;
        }
    }
}
