package com.nexo.app.modules.chamado.application.dto;

import com.nexo.app.modules.chamado.domain.PrioridadeChamado;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record TopicoChamadoRequest(
        @NotBlank @Size(max = 50) String codigo,
        @NotBlank @Size(max = 100) String nome,
        @NotBlank @Size(max = 500) String descricao,
        PrioridadeChamado prioridadePadrao,
        @NotNull UUID equipePadraoId,
        @NotNull @Min(1) Integer slaPrimeiraRespostaHoras,
        @NotNull @Min(1) Integer slaResolucaoHoras,
        boolean pausarSlaAguardandoSolicitante,
        @NotNull @Min(1) Integer prazoReaberturaHoras,
        Integer ordemExibicao
) {}
