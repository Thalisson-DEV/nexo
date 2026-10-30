package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.application.port.NormalizacaoTranscricaoGateway;
import com.nexo.app.modules.sst.domain.StatusNormalizacaoTranscricaoSst;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class NormalizacaoTranscricaoDto {
    private NormalizacaoTranscricaoDto() {}

    public record SolicitarRequest(
            @NotNull UUID idRevisaoTranscricao,
            @NotNull UUID clientRequestId) {}

    public record Response(
            UUID id,
            UUID idReuniao,
            UUID idRevisaoTranscricao,
            int numero,
            UUID clientRequestId,
            StatusNormalizacaoTranscricaoSst status,
            NormalizacaoTranscricaoGateway.Conteudo conteudo,
            String sha256Conteudo,
            String versaoModelo,
            String codigoFalha,
            OffsetDateTime concluidaEm,
            OffsetDateTime confirmadaEm,
            OffsetDateTime dataCriacao) {}
}
