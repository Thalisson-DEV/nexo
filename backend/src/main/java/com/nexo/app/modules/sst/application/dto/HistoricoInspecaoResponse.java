package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.shared.application.dto.Paged;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record HistoricoInspecaoResponse(
        List<Evento> eventos,
        Paged page,
        List<IntegridadeCadeia> integridade
) {
    public record Evento(
            UUID id,
            UUID idInspecao,
            long sequencia,
            String tipo,
            Autor autor,
            String origem,
            String justificativa,
            OffsetDateTime ocorridoEm,
            Object payload
    ) {}

    public record Autor(UUID id, String nome) {}

    public record AlteracaoItem(
            String caminho,
            Object valorAnterior,
            Object valorNovo
    ) {}

    public record IntegridadeCadeia(
            String escopo,
            UUID idCadeia,
            String status,
            Long sequenciaInicial,
            Long sequenciaFinal
    ) {}
}
