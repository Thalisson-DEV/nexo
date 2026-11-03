package com.nexo.app.modules.obras.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nexo.app.modules.obras.domain.ClasseTensao;
import com.nexo.app.modules.obras.domain.PrioridadeObra;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Dados de carteira da obra. {@code distanciaBaseKm} nulo significa deslocamento não calculado (obra sem
 * coordenadas ou cálculo pendente); {@code deslocamentoEstimado} indica cálculo em linha reta corrigida, sem
 * motor de rotas.
 */
public record CarteiraObraResponse(
        UUID idContrato,
        ClasseTensao classeTensao,
        String classeTensaoRotulo,
        String classeTensaoInformadaPor,
        OffsetDateTime classeTensaoInformadaEm,
        @JsonFormat(pattern = "dd/MM/yyyy") LocalDate prazoRegulado,
        PrioridadeObra prioridade,
        String lotePrevisto,
        BigDecimal valorMaterialPrevisto,
        BigDecimal valorServicoPrevisto,
        String observacaoStatus,
        BigDecimal distanciaBaseKm,
        Integer tempoDeslocamentoMin,
        Boolean deslocamentoEstimado,
        OffsetDateTime deslocamentoCalculadoEm
) {
}
