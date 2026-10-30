package com.nexo.app.modules.assistente.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Enderecos publicos das conexoes de IA. O Nexo e servido num endereco so: o app encaminha {@code /api}
 * e {@code /.well-known} ao backend. A base vem de {@code CES_URL_PUBLICA} ou, sem ela, da requisicao
 * (X-Forwarded-* do proxy). Fora de localhost a base e sempre https: o proxy do app recebe http do edge.
 * Em localhost, sem configuracao, vale o endereco do app liberado no CORS.
 */
@Component
public class UrlPublicaIa {

    public static final String CAMINHO_MCP = "/api/mcp";

    private final String configurada;
    private final String appLocal;

    public UrlPublicaIa(@Value("${ces.conexoes-ia.url-publica:}") String configurada,
            @Value("${ces.cors.allowed-origins:}") String origens) {
        this.configurada = configurada == null ? "" : configurada.strip().replaceAll("/+$", "");
        this.appLocal = primeiraOrigemLocal(origens);
    }

    /** Base do Nexo, sem barra no fim (ex.: https://app.exemplo.com.br). Tambem e o emissor (issuer) do OAuth. */
    public String base(HttpServletRequest request) {
        if (!configurada.isEmpty()) return configurada;
        String host = primeiro(request.getHeader("X-Forwarded-Host"));
        if (host == null) host = request.getHeader("Host");
        if (host == null) host = request.getServerName() + ":" + request.getServerPort();
        String esquema = primeiro(request.getHeader("X-Forwarded-Proto"));
        if (esquema == null) esquema = request.getScheme();
        if (local(host) && appLocal != null) return appLocal;
        if (!local(host)) esquema = "https";
        return esquema.toLowerCase(Locale.ROOT) + "://" + host;
    }

    public String mcp(HttpServletRequest request) {
        return base(request) + CAMINHO_MCP;
    }

    /** Metadados do recurso protegido (RFC 9728), no caminho derivado do endereco do MCP. */
    public String metadadosRecurso(HttpServletRequest request) {
        return base(request) + "/.well-known/oauth-protected-resource" + CAMINHO_MCP;
    }

    private static boolean local(String host) {
        String nome = host.toLowerCase(Locale.ROOT);
        return nome.startsWith("localhost") || nome.startsWith("127.") || nome.startsWith("[::1]");
    }

    /**
     * Desenvolvimento: a tela chama o backend direto (outra porta), mas o endereco publico e o do app, que
     * encaminha {@code /api} e {@code /.well-known}. Usa a primeira origem local liberada no CORS.
     */
    private static String primeiraOrigemLocal(String origens) {
        if (origens == null) return null;
        for (String origem : origens.split(",")) {
            String limpa = origem.strip().replaceAll("/+$", "");
            String host = limpa.replaceFirst("^https?://", "");
            if (!limpa.isEmpty() && !limpa.contains("*") && local(host)) return limpa;
        }
        return null;
    }

    private static String primeiro(String cabecalho) {
        if (cabecalho == null || cabecalho.isBlank()) return null;
        return cabecalho.split(",")[0].strip();
    }
}
