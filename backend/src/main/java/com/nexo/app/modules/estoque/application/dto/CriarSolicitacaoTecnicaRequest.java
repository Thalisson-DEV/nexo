package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Criacao tecnica; cada item informa o tipo oficial e cada tipo gera uma solicitacao independente")
public record CriarSolicitacaoTecnicaRequest(
        @NotNull UUID idObra,
        @Size(max = 100) String ocorrencia,
        @NotNull UUID idAlmoxarifadoAtendimento,
        UUID idEquipe,
        UUID idColaboradorSaque,
        Boolean saqueForaEquipe,
        LocalDate dataPrevista,
        @Size(max = 1000) String observacao,
        @NotEmpty List<@Valid Item> itens
) {
    public CriarSolicitacaoTecnicaRequest(
            UUID idObra,
            String ocorrencia,
            UUID idAlmoxarifadoAtendimento,
            UUID idEquipe,
            LocalDate dataPrevista,
            String observacao,
            List<Item> itens
    ) {
        this(idObra, ocorrencia, idAlmoxarifadoAtendimento, idEquipe, null, false, dataPrevista, observacao, itens);
    }
    public record Item(
            @NotNull UUID idMaterial,
            @NotNull TipoSolicitacaoTecnica tipoMovimentacao,
            @NotNull @DecimalMin(value = "0.001") BigDecimal quantidade
    ) {
    }
}
