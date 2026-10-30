package com.nexo.app.modules.chamado.application.dto;

import com.nexo.app.modules.chamado.domain.PrioridadeChamado;
import com.nexo.app.modules.chamado.domain.StatusChamado;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ChamadoFilter(
        @Schema(description = "Busca por protocolo, assunto ou descrição")
        String buscaGlobal,
        String protocolo,
        UUID topicoId,
        UUID equipeId,
        PrioridadeChamado prioridade,
        StatusChamado status,
        UUID usuarioSolicitanteId,
        UUID usuarioResponsavelId,
        OffsetDateTime dataAberturaInicio,
        OffsetDateTime dataAberturaFim,
        @Schema(description = "Filtra violação do SLA de primeira resposta")
        Boolean slaPrimeiraRespostaVencido,
        @Schema(description = "Filtra violação do SLA de resolução")
        Boolean slaResolucaoVencido,
        Boolean ativo
) {}
