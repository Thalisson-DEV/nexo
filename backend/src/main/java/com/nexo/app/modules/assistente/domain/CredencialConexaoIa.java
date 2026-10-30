package com.nexo.app.modules.assistente.domain;

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

/**
 * Credencial de uma conexao de IA (token de acesso, de renovacao ou chave). Registro tecnico: guarda so
 * o hash SHA-256 e nao passa pela auditoria generica, para o hash nunca ir ao log.
 */
@Entity
@Table(name = "assistente_conexao_credencial")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CredencialConexaoIa {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conexao_id", nullable = false, updatable = false)
    private UUID conexaoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, updatable = false, length = 10)
    private TipoCredencialIa tipo;

    @Column(name = "hash", nullable = false, updatable = false, length = 64)
    private String hash;

    @Column(name = "expira_em")
    private OffsetDateTime expiraEm;

    @Column(name = "usada_em")
    private OffsetDateTime usadaEm;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private OffsetDateTime dataCriacao;

    public CredencialConexaoIa(UUID conexaoId, TipoCredencialIa tipo, String hash, OffsetDateTime expiraEm) {
        this.conexaoId = Objects.requireNonNull(conexaoId, "conexaoId");
        this.tipo = Objects.requireNonNull(tipo, "tipo");
        this.hash = Objects.requireNonNull(hash, "hash");
        this.expiraEm = expiraEm;
        this.dataCriacao = OffsetDateTime.now();
    }

    public boolean isValida(OffsetDateTime agora) {
        return usadaEm == null && (expiraEm == null || expiraEm.isAfter(agora));
    }

    /** Token de renovacao e de uso unico: depois de trocado, nao vale mais. */
    public void marcarUsada() {
        this.usadaEm = OffsetDateTime.now();
    }
}
