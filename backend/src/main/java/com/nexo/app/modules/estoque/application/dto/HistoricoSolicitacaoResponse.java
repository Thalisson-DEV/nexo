package com.nexo.app.modules.estoque.application.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexo.app.modules.estoque.domain.EscopoEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.OrigemComandoTecnico;
import com.nexo.app.modules.estoque.domain.StatusIntegridadeHistorico;
import com.nexo.app.modules.estoque.domain.TipoEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.TipoObraSnapshot;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
import com.nexo.app.shared.application.dto.Paged;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record HistoricoSolicitacaoResponse(
        List<Evento> eventos,
        Paged page,
        List<IntegridadeCadeia> integridade
) {
    public record Evento(
            UUID id,
            EscopoEventoSolicitacao escopo,
            UUID idSolicitacao,
            long sequencia,
            TipoEventoSolicitacao tipo,
            TipoSolicitacaoTecnica tipoTecnico,
            TipoObraSnapshot tipoObra,
            String ocorrencia,
            Autor autor,
            OrigemComandoTecnico origem,
            String justificativa,
            long versao,
            OffsetDateTime ocorridoEm,
            Object payload
    ) {
    }

    public record Autor(UUID id, String nome) {
    }

    public record IntegridadeCadeia(
            EscopoEventoSolicitacao escopo,
            UUID idCadeia,
            StatusIntegridadeHistorico status,
            Long sequenciaInicial,
            Long sequenciaFinal
    ) {
    }
}
