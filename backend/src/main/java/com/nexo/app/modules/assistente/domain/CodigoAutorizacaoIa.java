package com.nexo.app.modules.assistente.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * Codigo de autorizacao do OAuth emitido quando o usuario permite a conexao na tela de consentimento.
 * Uso unico, vida curta e preso ao PKCE (S256): so quem iniciou o fluxo consegue troca-lo por token.
 */
@Entity
@Table(name = "assistente_conexao_codigo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CodigoAutorizacaoIa {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "hash", nullable = false, updatable = false, length = 64)
    private String hash;

    @Column(name = "client_id", nullable = false, updatable = false, length = 80)
    private String clientId;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Column(name = "permite_escrita", nullable = false, updatable = false)
    private boolean permiteEscrita;

    @Column(name = "redirect_uri", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String redirectUri;

    @Column(name = "code_challenge", nullable = false, updatable = false, length = 128)
    private String codeChallenge;

    @Column(name = "recurso", updatable = false, length = 500)
    private String recurso;

    @Column(name = "expira_em", nullable = false, updatable = false)
    private OffsetDateTime expiraEm;

    @Column(name = "usado_em")
    private OffsetDateTime usadoEm;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private OffsetDateTime dataCriacao;

    public CodigoAutorizacaoIa(String hash, String clientId, UUID usuarioId, boolean permiteEscrita, String redirectUri,
            String codeChallenge, String recurso, OffsetDateTime expiraEm) {
        this.hash = Objects.requireNonNull(hash, "hash");
        this.clientId = Objects.requireNonNull(clientId, "clientId");
        this.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId");
        this.permiteEscrita = permiteEscrita;
        this.redirectUri = Objects.requireNonNull(redirectUri, "redirectUri");
        this.codeChallenge = Objects.requireNonNull(codeChallenge, "codeChallenge");
        this.recurso = recurso;
        this.expiraEm = Objects.requireNonNull(expiraEm, "expiraEm");
        this.dataCriacao = OffsetDateTime.now();
    }

    public boolean isUtilizavel(OffsetDateTime agora) {
        return usadoEm == null && expiraEm.isAfter(agora);
    }

    public void marcarUsado() {
        this.usadoEm = OffsetDateTime.now();
    }

    /** PKCE S256: BASE64URL(SHA-256(code_verifier)) precisa ser o desafio enviado na autorizacao. */
    public boolean confereVerificador(String codeVerifier) {
        if (codeVerifier == null || codeVerifier.length() < 43 || codeVerifier.length() > 128) return false;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            String calculado = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
            return MessageDigest.isEqual(calculado.getBytes(StandardCharsets.US_ASCII),
                    codeChallenge.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }
}
