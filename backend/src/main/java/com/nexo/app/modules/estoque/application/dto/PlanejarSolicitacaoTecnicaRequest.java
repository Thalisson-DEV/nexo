package com.nexo.app.modules.estoque.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.json.JsonMapper;
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
import java.util.Map;
import java.util.UUID;
import java.io.IOException;

/** Snapshot completo da parcela mutavel de uma solicitacao tecnica. */
@Schema(description = "Substituicao completa e concorrente do planejamento de uma solicitacao")
@JsonDeserialize(using = PlanejarSolicitacaoTecnicaRequestDeserializer.class)
@JsonIgnoreProperties({"ocorrenciaInformada", "idEquipeInformado", "dataPrevistaInformada", "observacaoInformada", "idColaboradorSaqueInformado", "saqueForaEquipeInformado"})
@SnapshotPlanejamentoCompleto
public record PlanejarSolicitacaoTecnicaRequest(
        @NotNull Long versaoEsperada,
        @NotNull UUID idObra,
        @Size(max = 100) String ocorrencia,
        @NotNull UUID idAlmoxarifadoAtendimento,
        UUID idEquipe,
        UUID idColaboradorSaque,
        Boolean saqueForaEquipe,
        LocalDate dataPrevista,
        @Size(max = 1000) String observacao,
        @NotEmpty List<@Valid Item> itens,
        @Size(max = 500) String justificativa,
        @JsonIgnore @Schema(hidden = true) boolean ocorrenciaInformada,
        @JsonIgnore @Schema(hidden = true) boolean idEquipeInformado,
        @JsonIgnore @Schema(hidden = true) boolean dataPrevistaInformada,
        @JsonIgnore @Schema(hidden = true) boolean observacaoInformada,
        @JsonIgnore @Schema(hidden = true) boolean idColaboradorSaqueInformado,
        @JsonIgnore @Schema(hidden = true) boolean saqueForaEquipeInformado
) {
    public static PlanejarSolicitacaoTecnicaRequest fromJson(String json) throws IOException {
        return JsonMapper.builder().findAndAddModules().build()
                .readValue(json, PlanejarSolicitacaoTecnicaRequest.class);
    }

    public PlanejarSolicitacaoTecnicaRequest(
            Long versao,
            UUID idObra,
            String ocorrencia,
            UUID idAlmoxarifadoAtendimento,
            UUID idEquipe,
            UUID idColaboradorSaque,
            Boolean saqueForaEquipe,
            LocalDate dataPrevista,
            String observacao,
            List<Item> itens,
            String justificativa,
            Map<UUID, Long> versoesFilhos
    ) {
        this(versao, idObra, ocorrencia, idAlmoxarifadoAtendimento, idEquipe, idColaboradorSaque,
                saqueForaEquipe != null && saqueForaEquipe, dataPrevista,
                observacao, itens, justificativa, true, true, true, true, true, true);
    }

    public PlanejarSolicitacaoTecnicaRequest(
            Long versao,
            UUID idObra,
            String ocorrencia,
            UUID idAlmoxarifadoAtendimento,
            UUID idEquipe,
            LocalDate dataPrevista,
            String observacao,
            List<Item> itens,
            String justificativa,
            Map<UUID, Long> versoesFilhos
    ) {
        this(versao, idObra, ocorrencia, idAlmoxarifadoAtendimento, idEquipe, null, false, dataPrevista,
                observacao, itens, justificativa, true, true, true, true, false, false);
    }

    public PlanejarSolicitacaoTecnicaRequest(
            Long versao,
            UUID idObra,
            String ocorrencia,
            UUID idAlmoxarifadoAtendimento,
            UUID idEquipe,
            LocalDate dataPrevista,
            String observacao,
            List<Item> itens,
            String justificativa,
            boolean ocorrenciaInformada,
            boolean idEquipeInformado,
            boolean dataPrevistaInformada,
            boolean observacaoInformada
    ) {
        this(versao, idObra, ocorrencia, idAlmoxarifadoAtendimento, idEquipe, null, false,
                dataPrevista, observacao, itens, justificativa, ocorrenciaInformada,
                idEquipeInformado, dataPrevistaInformada, observacaoInformada, false, false);
    }

    /**
     * Mantido para chamadas Java já compiladas durante a transição do contrato.
     * Os endpoints devem enviar explicitamente as versões dos filhos existentes.
     */
    public PlanejarSolicitacaoTecnicaRequest(
            Long versao,
            UUID idObra,
            String ocorrencia,
            UUID idAlmoxarifadoAtendimento,
            UUID idEquipe,
            LocalDate dataPrevista,
            String observacao,
            List<Item> itens
    ) {
        this(versao, idObra, ocorrencia, idAlmoxarifadoAtendimento, idEquipe, null, false,
                dataPrevista, observacao, itens, null, true, true, true, true, false, false);
    }

    public record Item(
            UUID idItem,
            @NotNull UUID idMaterial,
            @NotNull TipoSolicitacaoTecnica tipoMovimentacao,
            @NotNull @DecimalMin("0.001") BigDecimal quantidade
    ) {
    }
}
