package com.nexo.app.modules.assistente.domain;

import com.nexo.app.shared.domain.AuditIgnore;
import com.nexo.app.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "assistente_mensagem",
        uniqueConstraints = @UniqueConstraint(name = "uk_assistente_mensagem_cliente_papel",
                columnNames = {"conversa_id", "client_message_id", "papel"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MensagemAssistente extends BaseEntity {

    public static final String CONTEUDO_PROCESSANDO = "Processando pergunta...";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversa_id", nullable = false, updatable = false)
    private ConversaAssistente conversa;

    @Column(name = "client_message_id", nullable = false, updatable = false)
    private UUID clientMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "papel", nullable = false, length = 20, updatable = false)
    private PapelMensagem papel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StatusMensagemAssistente status;

    @Column(name = "conteudo", nullable = false, columnDefinition = "TEXT")
    private String conteudo;

    @Column(name = "evidencias_json", columnDefinition = "TEXT")
    private String evidenciasJson;

    @Column(name = "acoes_json", columnDefinition = "TEXT")
    private String acoesJson;

    /** Pacotes do streaming compactados: permitem reexibir a timeline de ferramentas no historico. */
    @AuditIgnore
    @Column(name = "pacotes_json", columnDefinition = "TEXT")
    private String pacotesJson;

    /** Referencias (ids) dos anexos enviados junto com a pergunta. */
    @Column(name = "arquivos_json", columnDefinition = "TEXT")
    private String arquivosJson;

    /**
     * Snapshot do contexto de tela (painel, periodo, filtros) no momento da pergunta. Mantem o historico
     * semanticamente correto quando o usuario muda filtros entre uma pergunta e outra.
     */
    @AuditIgnore
    @Column(name = "contexto_json", columnDefinition = "TEXT")
    private String contextoJson;

    @Column(name = "modelo", length = 160)
    private String modelo;

    @Column(name = "tokens_entrada")
    private Integer tokensEntrada;

    @Column(name = "tokens_saida")
    private Integer tokensSaida;

    @Column(name = "custo_estimado", precision = 14, scale = 6)
    private BigDecimal custoEstimado;

    @Column(name = "duracao_ms")
    private Long duracaoMs;

    @Column(name = "ciclos")
    private Integer ciclos;

    @Column(name = "chamadas_ferramenta")
    private Integer chamadasFerramenta;

    @Column(name = "erro", length = 500)
    private String erro;

    private MensagemAssistente(ConversaAssistente conversa, UUID clientMessageId, PapelMensagem papel,
            StatusMensagemAssistente status, String conteudo, String evidenciasJson, String acoesJson) {
        this.conversa = Objects.requireNonNull(conversa, "Conversa e obrigatoria");
        this.clientMessageId = Objects.requireNonNull(clientMessageId, "ID da mensagem e obrigatorio");
        this.papel = Objects.requireNonNull(papel, "Papel e obrigatorio");
        this.status = Objects.requireNonNull(status, "Status e obrigatorio");
        this.conteudo = requireContent(conteudo);
        this.evidenciasJson = evidenciasJson;
        this.acoesJson = acoesJson;
    }

    public static MensagemAssistente pergunta(ConversaAssistente conversa, UUID clientMessageId, String conteudo) {
        return new MensagemAssistente(conversa, clientMessageId, PapelMensagem.USUARIO,
                StatusMensagemAssistente.CONCLUIDA, conteudo, null, null);
    }

    public static MensagemAssistente pergunta(ConversaAssistente conversa, UUID clientMessageId, String conteudo,
            String arquivosJson) {
        MensagemAssistente mensagem = pergunta(conversa, clientMessageId, conteudo);
        mensagem.arquivosJson = arquivosJson;
        return mensagem;
    }

    public static MensagemAssistente pergunta(ConversaAssistente conversa, UUID clientMessageId, String conteudo,
            String arquivosJson, String contextoJson) {
        MensagemAssistente mensagem = pergunta(conversa, clientMessageId, conteudo, arquivosJson);
        mensagem.contextoJson = contextoJson == null || contextoJson.isBlank() ? null : contextoJson;
        return mensagem;
    }

    public static MensagemAssistente resposta(ConversaAssistente conversa, UUID clientMessageId,
            StatusMensagemAssistente status, String conteudo, String evidenciasJson, String acoesJson) {
        if (status == StatusMensagemAssistente.PROCESSANDO) {
            throw new IllegalArgumentException("Resposta final nao pode permanecer processando");
        }
        return new MensagemAssistente(conversa, clientMessageId, PapelMensagem.ASSISTENTE,
                status, conteudo, evidenciasJson, acoesJson);
    }

    public static MensagemAssistente processando(ConversaAssistente conversa, UUID clientMessageId) {
        return new MensagemAssistente(conversa, clientMessageId, PapelMensagem.ASSISTENTE,
                StatusMensagemAssistente.PROCESSANDO, CONTEUDO_PROCESSANDO, "[]", "[]");
    }

    public void concluirResposta(StatusMensagemAssistente status, String conteudo,
            String evidenciasJson, String acoesJson) {
        if (this.papel != PapelMensagem.ASSISTENTE) {
            throw new IllegalStateException("Somente uma mensagem do assistente pode ser concluida");
        }
        if (status == null || status == StatusMensagemAssistente.PROCESSANDO) {
            throw new IllegalArgumentException("Status final invalido");
        }
        this.status = status;
        this.conteudo = requireContent(conteudo);
        this.evidenciasJson = evidenciasJson;
        this.acoesJson = acoesJson;
    }

    /** Conclui uma execucao agentic registrando a timeline e as metricas do provedor. */
    public void concluirExecucao(StatusMensagemAssistente status, String conteudo, String pacotesJson,
            String acoesJson, MetricasExecucao metricas, String erro) {
        concluirResposta(status, conteudo == null || conteudo.isBlank() ? fallbackContent(status) : conteudo,
                "[]", acoesJson);
        this.pacotesJson = pacotesJson;
        this.erro = erro == null ? null : erro.length() <= 500 ? erro : erro.substring(0, 497) + "...";
        if (metricas != null) {
            this.modelo = metricas.modelo();
            this.tokensEntrada = metricas.tokensEntrada();
            this.tokensSaida = metricas.tokensSaida();
            this.custoEstimado = metricas.custoEstimado();
            this.duracaoMs = metricas.duracaoMs();
            this.ciclos = metricas.ciclos();
            this.chamadasFerramenta = metricas.chamadasFerramenta();
        }
    }

    public boolean isProcessando() {
        return status == StatusMensagemAssistente.PROCESSANDO;
    }

    private static String fallbackContent(StatusMensagemAssistente status) {
        return switch (status) {
            case CANCELADA -> "Geração interrompida pelo usuário.";
            case FALHOU -> "Não foi possível concluir esta resposta.";
            case INDISPONIVEL -> "O assistente está indisponível no momento.";
            default -> "Sem conteúdo.";
        };
    }

    private String requireContent(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Conteudo e obrigatorio");
        return value.trim();
    }

    public record MetricasExecucao(
            String modelo,
            Integer tokensEntrada,
            Integer tokensSaida,
            BigDecimal custoEstimado,
            Long duracaoMs,
            Integer ciclos,
            Integer chamadasFerramenta) {
    }
}
