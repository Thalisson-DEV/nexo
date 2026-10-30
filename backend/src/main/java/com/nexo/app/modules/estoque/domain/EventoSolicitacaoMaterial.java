package com.nexo.app.modules.estoque.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "eventos_solicitacao_material")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventoSolicitacaoMaterial {

    public static final int FORMATO_ASSINATURA_ATUAL = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "escopo", nullable = false, length = 20)
    private EscopoEventoSolicitacao escopo;

    @Column(name = "id_solicitacao", nullable = false)
    private UUID idSolicitacao;

    @Column(name = "sequencia", nullable = false)
    private long sequencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", nullable = false, length = 80)
    private TipoEventoSolicitacao tipoEvento;

    @Column(name = "tipo_tecnico", length = 3)
    private String tipoTecnico;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_obra_snapshot", nullable = false, length = 20)
    private TipoObraSnapshot tipoObraSnapshot;

    @Column(name = "id_ocorrencia")
    private UUID idOcorrencia;

    @Column(name = "codigo_ocorrencia_snapshot", length = 100)
    private String ocorrencia;

    @Column(name = "id_autor")
    private UUID idAutor;

    @Column(name = "autor_snapshot", nullable = false, length = 200)
    private String autorSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", nullable = false, length = 20)
    private OrigemComandoTecnico origem;

    @Column(name = "justificativa", length = 1000)
    private String justificativa;

    @Column(name = "versao_solicitacao", nullable = false)
    private long versaoSolicitacao;

    /**
     * O texto canonico participa do HMAC. JSONB reserializa o valor ao ser
     * lido (incluindo espacos e ordem de chaves), invalidando uma assinatura
     * calculada antes do INSERT; a migration V133 preserva o texto exato.
     */
    @Column(name = "payload_canonico", nullable = false, columnDefinition = "TEXT")
    private String payloadCanonico;

    @Column(name = "hash_anterior", length = 64)
    private String hashAnterior;

    @Column(name = "hash_atual", length = 64)
    private String hashAtual;

    @Column(name = "versao_chave", length = 50)
    private String versaoChave;

    @Column(name = "versao_formato_hash", nullable = false)
    private int versaoFormatoHash = FORMATO_ASSINATURA_ATUAL;

    @Column(name = "ocorrido_em", nullable = false)
    private OffsetDateTime ocorridoEm;

    public EventoSolicitacaoMaterial(
            UUID idSolicitacao,
            long sequencia,
            TipoEventoSolicitacao tipoEvento,
            TipoSolicitacaoTecnica tipoTecnico,
            TipoObraSnapshot tipoObraSnapshot,
            UUID idOcorrencia,
            String ocorrencia,
            UUID idAutor,
            String autorSnapshot,
            OrigemComandoTecnico origem,
            String justificativa,
            long versaoSolicitacao,
            String payloadCanonico,
            OffsetDateTime ocorridoEm
    ) {
        this(EscopoEventoSolicitacao.SOLICITACAO, idSolicitacao, sequencia, tipoEvento,
                tipoTecnico, tipoObraSnapshot, idOcorrencia, ocorrencia, idAutor, autorSnapshot, origem,
                justificativa, versaoSolicitacao, payloadCanonico, ocorridoEm);
    }

    private EventoSolicitacaoMaterial(
            EscopoEventoSolicitacao escopo,
            UUID idSolicitacao,
            long sequencia,
            TipoEventoSolicitacao tipoEvento,
            TipoSolicitacaoTecnica tipoTecnico,
            TipoObraSnapshot tipoObraSnapshot,
            UUID idOcorrencia,
            String ocorrencia,
            UUID idAutor,
            String autorSnapshot,
            OrigemComandoTecnico origem,
            String justificativa,
            long versaoSolicitacao,
            String payloadCanonico,
            OffsetDateTime ocorridoEm
    ) {
        this.escopo = Objects.requireNonNull(escopo, "O escopo do evento e obrigatorio");
        this.idSolicitacao = idSolicitacao;
        validarEscopo();
        if (sequencia <= 0) {
            throw new IllegalArgumentException("A sequencia do evento deve ser positiva");
        }
        this.sequencia = sequencia;
        this.tipoEvento = Objects.requireNonNull(tipoEvento, "O tipo do evento e obrigatorio");
        this.tipoTecnico = tipoTecnico == null ? null : tipoTecnico.getCodigo();
        this.tipoObraSnapshot = tipoObraSnapshot;
        this.idOcorrencia = idOcorrencia;
        this.ocorrencia = ocorrencia;
        this.idAutor = idAutor;
        this.autorSnapshot = exigirTexto(autorSnapshot, "O snapshot do autor e obrigatorio");
        this.origem = Objects.requireNonNullElse(origem, OrigemComandoTecnico.SYSTEM);
        this.justificativa = limpar(justificativa);
        this.versaoSolicitacao = versaoSolicitacao;
        this.payloadCanonico = exigirTexto(payloadCanonico, "O payload canonico e obrigatorio");
        this.ocorridoEm = Objects.requireNonNull(ocorridoEm, "O instante do evento e obrigatorio");
    }

    public void selar(String hashAnterior, String hashAtual, String versaoChave) {
        if (this.hashAtual != null) {
            throw new IllegalStateException("O evento ja foi selado");
        }
        this.hashAnterior = hashAnterior;
        this.hashAtual = exigirTexto(hashAtual, "O hash atual e obrigatorio");
        this.versaoChave = exigirTexto(versaoChave, "A versao da chave e obrigatoria");
    }

    private void validarEscopo() {
        boolean solicitacaoValida = escopo == EscopoEventoSolicitacao.SOLICITACAO
                && idSolicitacao != null;
        if (!solicitacaoValida) {
            throw new IllegalArgumentException("As referencias nao correspondem ao escopo do evento");
        }
    }

    private static String exigirTexto(String valor, String mensagem) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensagem);
        }
        return valor.trim();
    }

    private static String limpar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
