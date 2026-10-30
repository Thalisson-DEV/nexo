package com.nexo.app.modules.assistente.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Acao de escrita proposta pelo agente e ainda nao executada. Fluxo obrigatorio:
 * plano -> previa -> confirmacao explicita do usuario -> execucao pela ferramenta de dominio -> auditoria.
 * O agente nunca executa escrita diretamente e nunca por SQL gerado.
 */
@Entity
@Table(name = "assistente_acao_pendente")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcaoPendenteAssistente {

    public enum Status { PENDENTE, CONFIRMADA, REJEITADA, EXPIRADA, FALHOU }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "conversa_id", nullable = false, updatable = false)
    private UUID conversaId;

    @Column(name = "mensagem_id", updatable = false)
    private UUID mensagemId;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Column(name = "ferramenta", nullable = false, length = 120, updatable = false)
    private String ferramenta;

    @Column(name = "argumentos_json", nullable = false, columnDefinition = "TEXT")
    private String argumentosJson;

    @Column(name = "previa_json", nullable = false, columnDefinition = "TEXT")
    private String previaJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "resultado_json", columnDefinition = "TEXT")
    private String resultadoJson;

    @Column(name = "expira_em", nullable = false, updatable = false)
    private OffsetDateTime expiraEm;

    @Column(name = "decidida_em")
    private OffsetDateTime decididaEm;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private OffsetDateTime dataCriacao;

    public AcaoPendenteAssistente(UUID conversaId, UUID mensagemId, UUID usuarioId, String ferramenta,
            String argumentosJson, String previaJson, OffsetDateTime expiraEm) {
        this.conversaId = Objects.requireNonNull(conversaId);
        this.mensagemId = mensagemId;
        this.usuarioId = Objects.requireNonNull(usuarioId);
        this.ferramenta = Objects.requireNonNull(ferramenta);
        this.argumentosJson = Objects.requireNonNull(argumentosJson);
        this.previaJson = Objects.requireNonNull(previaJson);
        this.expiraEm = Objects.requireNonNull(expiraEm);
        this.status = Status.PENDENTE;
        this.dataCriacao = OffsetDateTime.now();
    }

    public boolean expirada(OffsetDateTime agora) {
        return !expiraEm.isAfter(agora);
    }

    /** O usuario ajustou campos no cartao antes de confirmar: guarda os argumentos e a previa revisados. */
    public void revisar(String argumentosJson, String previaJson) {
        exigirPendente();
        this.argumentosJson = Objects.requireNonNull(argumentosJson);
        this.previaJson = Objects.requireNonNull(previaJson);
    }

    public void confirmar(String resultadoJson) {
        exigirPendente();
        this.status = Status.CONFIRMADA;
        this.resultadoJson = resultadoJson;
        this.decididaEm = OffsetDateTime.now();
    }

    public void rejeitar() {
        exigirPendente();
        this.status = Status.REJEITADA;
        this.decididaEm = OffsetDateTime.now();
    }

    public void falhar(String resultadoJson) {
        exigirPendente();
        this.status = Status.FALHOU;
        this.resultadoJson = resultadoJson;
        this.decididaEm = OffsetDateTime.now();
    }

    public void expirar() {
        if (status == Status.PENDENTE) {
            this.status = Status.EXPIRADA;
            this.decididaEm = OffsetDateTime.now();
        }
    }

    private void exigirPendente() {
        if (status != Status.PENDENTE) {
            throw new IllegalStateException("A acao ja foi decidida");
        }
    }
}
