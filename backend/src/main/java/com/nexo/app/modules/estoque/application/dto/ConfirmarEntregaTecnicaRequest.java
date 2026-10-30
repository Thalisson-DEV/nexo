package com.nexo.app.modules.estoque.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Dados imutáveis de uma confirmação de entrega técnica. */
@Schema(description = "Entrega de adicao 221/921; transfere material novo para a custodia da equipe")
public record ConfirmarEntregaTecnicaRequest(
        @NotNull Long versaoEsperada,
        @NotNull UUID idEquipe,
        UUID idColaboradorSaque,
        Boolean saqueForaEquipe,
        @NotBlank @Size(max = 262144) String assinaturaAlmox,
        @NotBlank @Size(max = 262144) String assinaturaEquipe,
        @NotEmpty List<@Valid ItemEntregaTecnicaRequest> itens,
        String numeroReserva,
        LocalDate dataReserva,
        UUID idAgenteReserva
) {
    public ConfirmarEntregaTecnicaRequest(
            Long versaoEsperada,
            UUID idEquipe,
            UUID idColaboradorSaque,
            Boolean saqueForaEquipe,
            String assinaturaAlmox,
            String assinaturaEquipe,
            List<ItemEntregaTecnicaRequest> itens
    ) {
        this(versaoEsperada, idEquipe, idColaboradorSaque, saqueForaEquipe, assinaturaAlmox, assinaturaEquipe, itens, null, null, null);
    }

    public ConfirmarEntregaTecnicaRequest(
            Long versaoEsperada,
            UUID idEquipe,
            String assinaturaAlmox,
            String assinaturaEquipe,
            List<ItemEntregaTecnicaRequest> itens
    ) {
        this(versaoEsperada, idEquipe, null, null, assinaturaAlmox, assinaturaEquipe, itens, null, null, null);
    }
    public record ItemEntregaTecnicaRequest(
            @NotNull UUID idItemSolicitacao,
            @NotNull @Positive BigDecimal quantidade,
            String numeroSerie
    ) {
    }
}
