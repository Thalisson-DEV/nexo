package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.OrigemTranscricaoSst;
import com.nexo.app.modules.sst.domain.StatusPresencaReuniaoSst;
import com.nexo.app.modules.sst.domain.StatusReuniaoGovernancaSst;
import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;
import com.nexo.app.modules.sst.domain.TipoReuniaoGovernancaSst;
import com.nexo.app.modules.sst.domain.TipoSecretarioReuniaoSst;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Contratos HTTP do fluxo guiado de reunião. */
public final class ReuniaoGovernancaDto {
    private ReuniaoGovernancaDto() {}

    public record Filter(
            String buscaGlobal,
            List<UUID> idsBases,
            List<UUID> idsCorpos,
            List<TipoCorpoGovernancaSst> tiposCorpo,
            List<TipoReuniaoGovernancaSst> tipos,
            List<StatusReuniaoGovernancaSst> status,
            OffsetDateTime dataInicio,
            OffsetDateTime dataFim) {}

    public record AgendarRequest(
            @NotNull UUID idCorpo,
            @NotNull UUID idModeloAta,
            @NotNull TipoReuniaoGovernancaSst tipo,
            @NotNull OffsetDateTime dataHora,
            @Min(1) @Max(1440) int duracaoMinutos,
            @NotBlank @Size(max = 255) String localOuMeio,
            @NotBlank @Size(max = 20_000) String pauta,
            UUID idResponsavel,
            boolean registroHistorico,
            @Size(max = 1_000) String justificativaHistorico,
            @Size(max = 1_000) String motivoExtraordinaria,
            OffsetDateTime dataFatoGerador,
            @Size(max = 120) String origemExtraordinaria) {}

    public record Response(
            UUID id,
            UUID idCorpo,
            UUID idBaseOperacional,
            TipoCorpoGovernancaSst tipoCorpo,
            TipoReuniaoGovernancaSst tipo,
            StatusReuniaoGovernancaSst status,
            OffsetDateTime dataHora,
            int duracaoMinutos,
            String localOuMeio,
            String pauta,
            UUID idResponsavel,
            UUID idModeloAta,
            Integer revisaoModeloAta,
            String snapshotModeloAta,
            boolean registroHistorico,
            String justificativaHistorico,
            String motivoExtraordinaria,
            OffsetDateTime dataFatoGerador,
            String origemExtraordinaria,
            TipoSecretarioReuniaoSst tipoSecretario,
            UUID idSecretarioParticipacao,
            UUID idSecretarioConvidado,
            UUID idVersaoAtaAprovada,
            OffsetDateTime iniciadaEm,
            OffsetDateTime presencasConcluidasEm,
            OffsetDateTime finalizadaEm,
            Long versao,
            OffsetDateTime dataCriacao) {}

    public record SelecionarModeloRequest(@NotNull UUID idModeloAta) {}

    public record PresencaRequest(
            @NotNull StatusPresencaReuniaoSst presenca,
            @Size(max = 1_000) String justificativa) {}

    public record ParticipacaoResponse(
            UUID id,
            UUID idMembroCorpo,
            UUID idColaborador,
            String nome,
            String funcao,
            String papel,
            String representacaoCipa,
            String titularidadeCipa,
            String turno,
            StatusPresencaReuniaoSst presenca,
            String justificativaAusencia,
            OffsetDateTime classificadaEm) {}

    public record ConvidadoRequest(
            UUID idColaborador,
            @NotBlank @Size(max = 255) String nome,
            @Size(max = 160) String funcao,
            @Size(max = 180) String organizacao,
            boolean presente) {}

    public record ConvidadoResponse(
            UUID id,
            UUID idColaborador,
            String nome,
            String funcao,
            String organizacao,
            boolean presente) {}

    public record SecretarioRequest(
            @NotNull TipoSecretarioReuniaoSst tipo,
            @NotNull UUID idPessoaNaReuniao) {}

    public record TranscricaoRequest(
            @NotNull OrigemTranscricaoSst origem,
            @NotBlank @Size(max = 60_000) String conteudo) {}

    public record TranscricaoResponse(
            UUID id,
            int numero,
            OrigemTranscricaoSst origem,
            String conteudo,
            String hashConteudo,
            boolean confirmada,
            OffsetDateTime confirmadaEm,
            OffsetDateTime dataCriacao) {}

    public record MotivoRequest(@NotBlank @Size(max = 1_000) String motivo) {}

    public record AlertaFaltaResponse(
            UUID id,
            UUID idMembroCorpo,
            int quantidadeFaltas,
            List<UUID> reunioesConsideradas,
            OffsetDateTime emitidoEm,
            OffsetDateTime conferidoEm) {}
            
    public record CapabilitiesResponse(
            boolean geracaoIaDisponivel,
            boolean normalizacaoIaDisponivel,
            boolean geracaoManualDisponivel) {}
}
