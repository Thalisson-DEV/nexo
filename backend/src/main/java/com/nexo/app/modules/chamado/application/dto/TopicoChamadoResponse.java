package com.nexo.app.modules.chamado.application.dto;

import com.nexo.app.modules.chamado.domain.PrioridadeChamado;

import java.util.UUID;

public record TopicoChamadoResponse(
        UUID id,
        String codigo,
        String nome,
        String descricao,
        PrioridadeChamado prioridadePadrao,
        EquipeAtendimentoResumoResponse equipePadrao,
        Integer slaPrimeiraRespostaHoras,
        Integer slaResolucaoHoras,
        boolean pausarSlaAguardandoSolicitante,
        Integer prazoReaberturaHoras,
        Integer ordemExibicao,
        boolean ativo
) {}
