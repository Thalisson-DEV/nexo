package com.nexo.app.modules.chamado.application.dto;

import com.nexo.app.modules.chamado.domain.PrioridadeChamado;
import com.nexo.app.modules.chamado.domain.StatusChamado;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ChamadoResponse(
        UUID id,
        String protocolo,
        TopicoChamadoResponse topico,
        EquipeAtendimentoResumoResponse equipeAtendimento,
        String assunto,
        String descricao,
        PrioridadeChamado prioridade,
        StatusChamado status,
        UUID usuarioSolicitanteId,
        UUID usuarioResponsavelId,
        String solucao,
        OffsetDateTime dataAbertura,
        OffsetDateTime dataLimitePrimeiraResposta,
        OffsetDateTime dataLimiteResolucao,
        boolean pausarSlaAguardandoSolicitante,
        Integer prazoReaberturaHoras,
        OffsetDateTime dataPrimeiraResposta,
        OffsetDateTime dataInicioAtendimento,
        OffsetDateTime dataInicioPausaSla,
        long totalSegundosPausaSla,
        OffsetDateTime dataResolucao,
        OffsetDateTime dataFechamento,
        OffsetDateTime dataUltimaInteracao,
        boolean slaPrimeiraRespostaVencido,
        boolean slaResolucaoVencido,
        boolean slaPausado,
        List<AnexoChamadoResponse> anexos,
        OffsetDateTime dataCriacao,
        OffsetDateTime dataUltimaAlteracao
) {}
