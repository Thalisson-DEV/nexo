package com.nexo.app.modules.assistente.domain;

import com.nexo.app.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Aplicativo de IA registrado pelo cadastro dinamico do OAuth (RFC 7591): Claude, ChatGPT, Cursor...
 * Cliente publico, sem segredo; a seguranca vem do PKCE e dos enderecos de retorno registrados.
 */
@Entity
@Table(name = "assistente_conexao_cliente")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClienteConexaoIa extends BaseEntity {

    public static final int NOME_MAXIMO = 120;

    @Column(name = "client_id", nullable = false, updatable = false, length = 80)
    private String clientId;

    @Column(name = "nome", nullable = false, length = NOME_MAXIMO)
    private String nome;

    /** Enderecos de retorno, um por linha. */
    @Column(name = "redirect_uris", nullable = false, columnDefinition = "TEXT")
    private String redirectUris;

    @Column(name = "site", length = 300)
    private String site;

    @Column(name = "ip_registro", length = 64)
    private String ipRegistro;

    public ClienteConexaoIa(String clientId, String nome, List<String> redirectUris, String site, String ipRegistro) {
        if (redirectUris == null || redirectUris.isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos um endereco de retorno");
        }
        this.clientId = Objects.requireNonNull(clientId, "clientId");
        this.nome = nomeLimpo(nome);
        this.redirectUris = String.join("\n", redirectUris);
        this.site = site == null || site.isBlank() ? null : site.strip();
        this.ipRegistro = ipRegistro;
    }

    public List<String> enderecosDeRetorno() {
        return Arrays.stream(redirectUris.split("\n")).filter(uri -> !uri.isBlank()).toList();
    }

    /** O retorno precisa ser exatamente um dos registrados (sem curinga, como pede o OAuth 2.1). */
    public boolean aceitaRetorno(String redirectUri) {
        return redirectUri != null && enderecosDeRetorno().contains(redirectUri);
    }

    private static String nomeLimpo(String nome) {
        String limpo = nome == null || nome.isBlank() ? "Aplicativo de IA" : nome.strip().replaceAll("\\s+", " ");
        return limpo.length() <= NOME_MAXIMO ? limpo : limpo.substring(0, NOME_MAXIMO);
    }
}
