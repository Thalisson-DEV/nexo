package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.OrigemVersaoAtaSst;
import com.nexo.app.modules.sst.domain.StatusTentativaGeracaoAtaSst;
import com.nexo.app.modules.sst.domain.StatusVersaoAtaSst;
import com.nexo.app.modules.sst.domain.TipoComunicacaoAtaCipaSst;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Contratos das versões de ata, geração assistida e arquivos privados. */
public final class AtaGovernancaDto {
    private AtaGovernancaDto() {}

    public record VersaoManualRequest(
            @NotBlank @Size(max = 80_000) String conteudo,
            UUID idVersaoBase) {}

    public record GerarRequest(
            @NotNull UUID clientRequestId,
            UUID idVersaoBase,
            @Size(max = 4_000) String instrucaoCorrecao) {}

    public record VersaoResponse(
            UUID id,
            int numero,
            OrigemVersaoAtaSst origem,
            StatusVersaoAtaSst status,
            String conteudo,
            String sha256Conteudo,
            UUID idVersaoBase,
            UUID idRevisaoTranscricao,
            UUID idModeloAta,
            int revisaoModeloAta,
            String snapshotModeloAta,
            String instrucaoCorrecao,
            String versaoPrompt,
            UUID idAutorOuSolicitante,
            OffsetDateTime aprovadaEm,
            UUID idUsuarioAprovacao,
            OffsetDateTime dataCriacao) {}

    public record TentativaResponse(
            UUID id,
            UUID clientRequestId,
            StatusTentativaGeracaoAtaSst status,
            UUID idVersaoGerada,
            String codigoFalha,
            VersaoResponse versao) {}

    public record ArquivoResponse(
            UUID id,
            UUID idVersaoAta,
            String nomeArquivo,
            String contentType,
            long tamanhoBytes,
            String sha256,
            OffsetDateTime enviadaEm) {}

    public record Download(java.io.InputStream conteudo, String nomeArquivo,
                           String contentType, long tamanhoBytes) {}

    public record EvidenciaFotoResponse(
            UUID id,
            String nomeArquivo,
            String contentType,
            long tamanhoBytes,
            String sha256,
            String legenda,
            UUID idAutor,
            OffsetDateTime enviadaEm) {}

    public record ComunicacaoRequest(
            @NotNull TipoComunicacaoAtaCipaSst tipo,
            @NotNull OffsetDateTime comunicadaEm,
            @NotBlank @Size(max = 255) String canal,
            @Size(max = 500) String alcance) {}

    public record ComunicacaoResponse(
            UUID id,
            UUID idVersaoAta,
            TipoComunicacaoAtaCipaSst tipo,
            OffsetDateTime comunicadaEm,
            String canal,
            String alcance,
            UUID idResponsavel,
            boolean possuiEvidencia,
            String nomeEvidencia,
            OffsetDateTime dataCriacao) {}
}
