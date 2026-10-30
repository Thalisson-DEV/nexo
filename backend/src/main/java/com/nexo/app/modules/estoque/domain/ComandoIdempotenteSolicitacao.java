package com.nexo.app.modules.estoque.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** Resultado durável de um comando crítico, protegido por escopo/usuário/chave. */
@Entity
@Table(name = "comandos_idempotentes_solicitacao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComandoIdempotenteSolicitacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "escopo", nullable = false, length = 100)
    private String escopo;

    @Column(name = "id_usuario", nullable = false)
    private UUID idUsuario;

    @Column(name = "chave", nullable = false, length = 200)
    private String chave;

    @Column(name = "hash_payload", nullable = false, length = 64)
    private String hashPayload;

    @Column(name = "id_solicitacao")
    private UUID idSolicitacao;

    @Column(name = "id_operacao")
    private UUID idOperacao;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resposta")
    private Map<String, String> resposta;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    public ComandoIdempotenteSolicitacao(
            String escopo,
            UUID idUsuario,
            String chave,
            String hashPayload,
            UUID idSolicitacao,
            Map<String, String> resposta
    ) {
        this(escopo, idUsuario, chave, hashPayload, idSolicitacao, null, resposta);
    }

    public ComandoIdempotenteSolicitacao(
            String escopo,
            UUID idUsuario,
            String chave,
            String hashPayload,
            UUID idSolicitacao,
            UUID idOperacao,
            Map<String, String> resposta
    ) {
        this.escopo = exigirTexto(escopo, "O escopo e obrigatorio");
        this.idUsuario = java.util.Objects.requireNonNull(idUsuario, "O usuario e obrigatorio");
        this.chave = exigirTexto(chave, "A chave de idempotencia e obrigatoria");
        this.hashPayload = exigirTexto(hashPayload, "O hash do payload e obrigatorio");
        this.idSolicitacao = idSolicitacao;
        this.idOperacao = idOperacao;
        this.resposta = Map.copyOf(java.util.Objects.requireNonNullElse(resposta, Map.of()));
        this.criadoEm = OffsetDateTime.now();
    }

    public boolean possuiMesmoPayload(String outroHash) {
        return hashPayload.equals(outroHash);
    }

    private static String exigirTexto(String valor, String mensagem) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensagem);
        }
        return valor.trim();
    }
}
