package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.AuditIgnore;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.persistence.CryptoConverterImpl;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Raiz do agregado de uma reunião de CIPA ou Brigada.
 *
 * <p>Participações, transcrições e atas vivem em tabelas próprias e referenciam o id
 * desta raiz. Os contadores de versão são incrementados sob lock pessimista no caso de
 * uso, impedindo dois rascunhos concorrentes de receberem o mesmo número.</p>
 */
@Entity
@Table(name = "reunioes_governanca_sst")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReuniaoGovernancaSst extends BaseEntity {

    private static final int TEXTO_CURTO_MAXIMO = 255;
    private static final int MOTIVO_MAXIMO = 1000;

    @Column(name = "id_corpo", nullable = false, updatable = false)
    private UUID idCorpo;

    @Column(name = "id_base_operacional", nullable = false, updatable = false)
    private UUID idBaseOperacional;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_corpo", nullable = false, updatable = false, length = 20)
    private TipoCorpoGovernancaSst tipoCorpo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private TipoReuniaoGovernancaSst tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StatusReuniaoGovernancaSst status;

    @Column(name = "data_hora", nullable = false)
    private OffsetDateTime dataHora;

    @Column(name = "duracao_minutos", nullable = false)
    private int duracaoMinutos;

    @Column(name = "local_ou_meio", nullable = false, length = TEXTO_CURTO_MAXIMO)
    private String localOuMeio;

    @AuditIgnore
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "pauta", nullable = false, columnDefinition = "text")
    private String pauta;

    @Column(name = "id_responsavel", nullable = false, updatable = false)
    private UUID idResponsavel;

    @Column(name = "id_modelo_ata")
    private UUID idModeloAta;

    @Column(name = "revisao_modelo_ata")
    private Integer revisaoModeloAta;

    @AuditIgnore
    @Column(name = "snapshot_modelo_ata", columnDefinition = "text")
    private String snapshotModeloAta;

    @Column(name = "registro_historico", nullable = false)
    private boolean registroHistorico;

    @AuditIgnore
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "justificativa_historico", columnDefinition = "text")
    private String justificativaHistorico;

    @AuditIgnore
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "motivo_extraordinaria", columnDefinition = "text")
    private String motivoExtraordinaria;

    @Column(name = "data_fato_gerador")
    private OffsetDateTime dataFatoGerador;

    @Column(name = "origem_extraordinaria", length = 120)
    private String origemExtraordinaria;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_secretario", length = 20)
    private TipoSecretarioReuniaoSst tipoSecretario;

    @Column(name = "id_secretario_participacao")
    private UUID idSecretarioParticipacao;

    @Column(name = "id_secretario_convidado")
    private UUID idSecretarioConvidado;

    @Column(name = "iniciada_em")
    private OffsetDateTime iniciadaEm;

    @Column(name = "presencas_concluidas_em")
    private OffsetDateTime presencasConcluidasEm;

    @Column(name = "id_versao_ata_aprovada")
    private UUID idVersaoAtaAprovada;

    @Column(name = "finalizada_em")
    private OffsetDateTime finalizadaEm;

    @Column(name = "id_usuario_finalizacao")
    private UUID idUsuarioFinalizacao;

    @AuditIgnore
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "motivo_reabertura", columnDefinition = "text")
    private String motivoReabertura;

    @Column(name = "reaberta_em")
    private OffsetDateTime reabertaEm;

    @Column(name = "id_usuario_reabertura")
    private UUID idUsuarioReabertura;

    @AuditIgnore
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "motivo_cancelamento", columnDefinition = "text")
    private String motivoCancelamento;

    @Column(name = "cancelada_em")
    private OffsetDateTime canceladaEm;

    @Column(name = "id_usuario_cancelamento")
    private UUID idUsuarioCancelamento;

    @Column(name = "proxima_revisao_transcricao", nullable = false)
    private int proximaRevisaoTranscricao;

    @Column(name = "proxima_versao_ata", nullable = false)
    private int proximaVersaoAta;

    public static ReuniaoGovernancaSst agendar(
            UUID idCorpo,
            UUID idBaseOperacional,
            TipoCorpoGovernancaSst tipoCorpo,
            TipoReuniaoGovernancaSst tipo,
            OffsetDateTime dataHora,
            int duracaoMinutos,
            String localOuMeio,
            String pauta,
            UUID idResponsavel,
            boolean registroHistorico,
            String justificativaHistorico,
            String motivoExtraordinaria,
            OffsetDateTime dataFatoGerador,
            String origemExtraordinaria) {
        ReuniaoGovernancaSst reuniao = new ReuniaoGovernancaSst();
        reuniao.idCorpo = Objects.requireNonNull(idCorpo, "O corpo de governança é obrigatório.");
        reuniao.idBaseOperacional = Objects.requireNonNull(idBaseOperacional, "A base é obrigatória.");
        reuniao.tipoCorpo = Objects.requireNonNull(tipoCorpo, "O tipo do corpo é obrigatório.");
        reuniao.tipo = Objects.requireNonNull(tipo, "O tipo da reunião é obrigatório.");
        reuniao.dataHora = Objects.requireNonNull(dataHora, "A data e hora da reunião são obrigatórias.");
        if (duracaoMinutos < 1 || duracaoMinutos > 1440) {
            throw new InvarianteVioladaException("Informe uma duração entre 1 minuto e 24 horas.");
        }
        reuniao.duracaoMinutos = duracaoMinutos;
        reuniao.localOuMeio = exigirTexto(localOuMeio, TEXTO_CURTO_MAXIMO,
                "Informe o local ou meio da reunião.");
        reuniao.pauta = exigirTexto(pauta, 20_000, "Informe a pauta da reunião.");
        reuniao.idResponsavel = Objects.requireNonNull(idResponsavel, "O responsável é obrigatório.");
        reuniao.registroHistorico = registroHistorico;
        reuniao.justificativaHistorico = textoOpcional(justificativaHistorico, MOTIVO_MAXIMO);
        if (registroHistorico && reuniao.justificativaHistorico == null) {
            throw new InvarianteVioladaException("Informe a justificativa do registro histórico.");
        }
        reuniao.aplicarFatoGeradorExtraordinario(motivoExtraordinaria, dataFatoGerador, origemExtraordinaria);
        reuniao.status = StatusReuniaoGovernancaSst.AGENDADA;
        reuniao.proximaRevisaoTranscricao = 0;
        reuniao.proximaVersaoAta = 0;
        return reuniao;
    }

    public void iniciar() {
        exigirStatus(StatusReuniaoGovernancaSst.AGENDADA,
                "Somente uma reunião agendada pode ser iniciada.");
        this.status = StatusReuniaoGovernancaSst.EM_REALIZACAO;
        this.iniciadaEm = OffsetDateTime.now();
    }

    /** O modelo só pode ser trocado antes da primeira versão de ata. */
    public void selecionarModeloAta(UUID idModelo, int revisaoModelo, String snapshotModelo) {
        if (proximaVersaoAta > 0 || idVersaoAtaAprovada != null) {
            throw new InvarianteVioladaException(
                    "O modelo não pode ser trocado depois da criação da primeira versão da ata.");
        }
        if (status == StatusReuniaoGovernancaSst.FINALIZADA
                || status == StatusReuniaoGovernancaSst.CANCELADA) {
            throw new InvarianteVioladaException("O estado da reunião não permite trocar o modelo da ata.");
        }
        this.idModeloAta = Objects.requireNonNull(idModelo, "Selecione um modelo de ata publicado.");
        if (revisaoModelo < 1) throw new InvarianteVioladaException("A revisão do modelo é inválida.");
        this.revisaoModeloAta = revisaoModelo;
        this.snapshotModeloAta = exigirTexto(snapshotModelo, 20_000,
                "Não foi possível preservar a estrutura do modelo selecionado.");
    }

    public void designarSecretarioParticipante(UUID idParticipacao) {
        exigirEmRealizacao();
        this.tipoSecretario = TipoSecretarioReuniaoSst.PARTICIPANTE;
        this.idSecretarioParticipacao = Objects.requireNonNull(idParticipacao,
                "Selecione a participação do secretário.");
        this.idSecretarioConvidado = null;
    }

    public void designarSecretarioConvidado(UUID idConvidado) {
        exigirEmRealizacao();
        this.tipoSecretario = TipoSecretarioReuniaoSst.CONVIDADO;
        this.idSecretarioConvidado = Objects.requireNonNull(idConvidado,
                "Selecione o convidado que atuará como secretário.");
        this.idSecretarioParticipacao = null;
    }

    public void concluirPresencas(long presencasPendentes) {
        exigirEmRealizacao();
        if (presencasPendentes > 0) {
            throw new InvarianteVioladaException(
                    "Registre a presença de todos os membros antes de concluir esta etapa.");
        }
        if (exigeSecretario() && tipoSecretario == null) {
            throw new InvarianteVioladaException("Designe o secretário desta reunião da CIPA.");
        }
        this.presencasConcluidasEm = OffsetDateTime.now();
        this.status = StatusReuniaoGovernancaSst.AGUARDANDO_ATA;
    }

    public int reservarNumeroRevisaoTranscricao() {
        exigirConteudoEditavel();
        return ++this.proximaRevisaoTranscricao;
    }

    public int reservarNumeroVersaoAta() {
        exigirConteudoEditavel();
        return ++this.proximaVersaoAta;
    }

    public void registrarAtaAprovada(UUID idVersaoAta) {
        exigirConteudoEditavel();
        this.idVersaoAtaAprovada = Objects.requireNonNull(idVersaoAta,
                "A versão aprovada da ata é obrigatória.");
        this.status = StatusReuniaoGovernancaSst.AGUARDANDO_ASSINATURAS;
    }

    public void finalizar(boolean possuiCopiaAssinada, UUID idUsuario) {
        exigirStatus(StatusReuniaoGovernancaSst.AGUARDANDO_ASSINATURAS,
                "A reunião precisa ter uma ata aprovada antes da finalização.");
        if (tipoCorpo == TipoCorpoGovernancaSst.CIPA && !possuiCopiaAssinada) {
            throw new InvarianteVioladaException(
                    "Anexe a cópia da ata assinada pelos presentes antes de finalizar a reunião da CIPA.");
        }
        this.idUsuarioFinalizacao = Objects.requireNonNull(idUsuario,
                "Não foi possível identificar quem finalizou a reunião.");
        this.finalizadaEm = OffsetDateTime.now();
        this.status = StatusReuniaoGovernancaSst.FINALIZADA;
    }

    public void reabrir(String motivo, UUID idUsuario) {
        exigirStatus(StatusReuniaoGovernancaSst.FINALIZADA,
                "Somente uma reunião finalizada pode ser reaberta.");
        this.motivoReabertura = exigirTexto(motivo, MOTIVO_MAXIMO,
                "Informe o motivo da reabertura.");
        this.idUsuarioReabertura = Objects.requireNonNull(idUsuario,
                "Não foi possível identificar quem reabriu a reunião.");
        this.reabertaEm = OffsetDateTime.now();
        this.finalizadaEm = null;
        this.idUsuarioFinalizacao = null;
        this.status = StatusReuniaoGovernancaSst.AGUARDANDO_ATA;
    }

    public void cancelar(String motivo, UUID idUsuario) {
        if (status == StatusReuniaoGovernancaSst.FINALIZADA) {
            throw new InvarianteVioladaException("Uma reunião finalizada não pode ser cancelada.");
        }
        if (status == StatusReuniaoGovernancaSst.CANCELADA) {
            throw new InvarianteVioladaException("Esta reunião já está cancelada.");
        }
        this.motivoCancelamento = exigirTexto(motivo, MOTIVO_MAXIMO,
                "Informe o motivo do cancelamento.");
        this.idUsuarioCancelamento = Objects.requireNonNull(idUsuario,
                "Não foi possível identificar quem cancelou a reunião.");
        this.canceladaEm = OffsetDateTime.now();
        this.status = StatusReuniaoGovernancaSst.CANCELADA;
    }

    public boolean exigeSecretario() {
        return tipoCorpo == TipoCorpoGovernancaSst.CIPA
                && (tipo == TipoReuniaoGovernancaSst.ORDINARIA
                    || tipo == TipoReuniaoGovernancaSst.EXTRAORDINARIA);
    }

    public boolean permiteAlterarParticipantes() {
        return status == StatusReuniaoGovernancaSst.EM_REALIZACAO;
    }

    public boolean permiteAlterarEvidencias() {
        return status != StatusReuniaoGovernancaSst.FINALIZADA
                && status != StatusReuniaoGovernancaSst.CANCELADA;
    }

    public boolean finalizada() {
        return status == StatusReuniaoGovernancaSst.FINALIZADA;
    }

    private void aplicarFatoGeradorExtraordinario(String motivo, OffsetDateTime dataFatoGerador,
                                                   String origemExtraordinaria) {
        if (tipo != TipoReuniaoGovernancaSst.EXTRAORDINARIA) {
            this.motivoExtraordinaria = null;
            this.dataFatoGerador = null;
            this.origemExtraordinaria = null;
            return;
        }
        this.motivoExtraordinaria = exigirTexto(motivo, MOTIVO_MAXIMO,
                "Informe o motivo da reunião extraordinária.");
        this.dataFatoGerador = Objects.requireNonNull(dataFatoGerador,
                "Informe a data do fato ou da solicitação que originou a reunião extraordinária.");
        this.origemExtraordinaria = exigirTexto(origemExtraordinaria, 120,
                "Informe a origem da reunião extraordinária.");
    }

    private void exigirEmRealizacao() {
        exigirStatus(StatusReuniaoGovernancaSst.EM_REALIZACAO,
                "A presença e o secretário só podem ser alterados durante a realização.");
    }

    private void exigirConteudoEditavel() {
        if (!status.permiteConteudo()) {
            throw new InvarianteVioladaException(
                    "A reunião não aceita transcrição ou nova versão de ata no estado atual.");
        }
    }

    private void exigirStatus(StatusReuniaoGovernancaSst esperado, String mensagem) {
        if (status != esperado) {
            throw new InvarianteVioladaException(mensagem);
        }
    }

    private static String exigirTexto(String valor, int maximo, String mensagem) {
        if (valor == null || valor.isBlank()) {
            throw new InvarianteVioladaException(mensagem);
        }
        String texto = valor.trim();
        if (texto.length() > maximo) {
            throw new InvarianteVioladaException("O texto excede o limite de " + maximo + " caracteres.");
        }
        return texto;
    }

    private static String textoOpcional(String valor, int maximo) {
        if (valor == null || valor.isBlank()) return null;
        return exigirTexto(valor, maximo, "O texto é obrigatório.");
    }
}
