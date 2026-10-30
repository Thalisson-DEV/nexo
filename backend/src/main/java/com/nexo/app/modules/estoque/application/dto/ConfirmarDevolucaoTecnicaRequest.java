package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.modules.estoque.domain.CondicaoMaterial;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Dados imutáveis de uma confirmação de devolução técnica. */
@Schema(description = "Recebimento de devolucao 222/922 sem separacao; retorna material novo ou sucata da equipe ao almoxarifado")
public record ConfirmarDevolucaoTecnicaRequest(
        @NotNull Long versaoEsperada,
        @NotNull UUID idEquipe,
        @Schema(description = "Numero da reserva SAP/concessionaria (opcional)")
        String numeroReserva,
        @Schema(description = "Data de emissao da reserva (opcional)")
        LocalDate dataReserva,
        @Schema(description = "Identificador do agente responsavel pela reserva (opcional)")
        UUID idAgenteReserva,
        @NotEmpty List<@Valid ItemDevolucaoTecnicaRequest> itens
) {
    public ConfirmarDevolucaoTecnicaRequest(
            Long versaoEsperada,
            UUID idEquipe,
            List<ItemDevolucaoTecnicaRequest> itens
    ) {
        this(versaoEsperada, idEquipe, null, null, null, itens);
    }
    public record ItemDevolucaoTecnicaRequest(
            @NotNull UUID idItemSolicitacao,
            @NotNull @Positive BigDecimal quantidade,
            String numeroSerie,
            CondicaoMaterial condicao
    ) {
        public ItemDevolucaoTecnicaRequest {
            condicao = condicao == null ? CondicaoMaterial.NOVO : condicao;
        }

        public ItemDevolucaoTecnicaRequest(
                UUID idItemSolicitacao,
                BigDecimal quantidade,
                String numeroSerie
        ) {
            this(idItemSolicitacao, quantidade, numeroSerie, CondicaoMaterial.NOVO);
        }
    }
}
