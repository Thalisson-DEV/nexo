package com.nexo.app.modules.assistente.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexo.app.modules.assistente.application.conexoes.OAuthErro;
import com.nexo.app.modules.assistente.application.conexoes.OAuthIaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Descoberta e endpoints publicos do OAuth das conexoes de IA. A tela de autorizacao e a do app
 * ({@code /conectar-ia/autorizar}): a pessoa entra com a conta do Nexo e decide ali.
 */
@RestController
public class OAuthIaController {

    private final OAuthIaService oauth;
    private final UrlPublicaIa urls;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public OAuthIaController(OAuthIaService oauth, UrlPublicaIa urls, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.oauth = oauth;
        this.urls = urls;
        this.objectMapper = objectMapper;
    }

    /** RFC 9728: metadados do recurso protegido (o servidor MCP). */
    @GetMapping(value = {"/.well-known/oauth-protected-resource", "/.well-known/oauth-protected-resource/**"},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> recursoProtegido(HttpServletRequest request) {
        Map<String, Object> metadados = new LinkedHashMap<>();
        metadados.put("resource", urls.mcp(request));
        metadados.put("authorization_servers", List.of(urls.base(request)));
        metadados.put("scopes_supported", List.of(OAuthIaService.ESCOPO_LEITURA, OAuthIaService.ESCOPO_ESCRITA));
        metadados.put("bearer_methods_supported", List.of("header"));
        metadados.put("resource_name", "Nexo — Gestão Operacional");
        return publico(metadados);
    }

    /** RFC 8414: metadados do servidor de autorizacao. */
    @GetMapping(value = {"/.well-known/oauth-authorization-server", "/.well-known/oauth-authorization-server/**"},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> servidorAutorizacao(HttpServletRequest request) {
        String base = urls.base(request);
        Map<String, Object> metadados = new LinkedHashMap<>();
        metadados.put("issuer", base);
        metadados.put("authorization_endpoint", base + "/conectar-ia/autorizar");
        metadados.put("token_endpoint", base + "/api/oauth/token");
        metadados.put("registration_endpoint", base + "/api/oauth/register");
        metadados.put("revocation_endpoint", base + "/api/oauth/revoke");
        metadados.put("scopes_supported", List.of(OAuthIaService.ESCOPO_LEITURA, OAuthIaService.ESCOPO_ESCRITA));
        metadados.put("response_types_supported", List.of("code"));
        metadados.put("response_modes_supported", List.of("query"));
        metadados.put("grant_types_supported", List.of("authorization_code", "refresh_token"));
        metadados.put("token_endpoint_auth_methods_supported", List.of("none"));
        metadados.put("revocation_endpoint_auth_methods_supported", List.of("none"));
        metadados.put("code_challenge_methods_supported", List.of("S256"));
        metadados.put("authorization_response_iss_parameter_supported", true);
        metadados.put("service_documentation", base + "/copiloto/conectar-ia");
        return publico(metadados);
    }

    /** RFC 7591: cadastro dinamico do aplicativo de IA. */
    @PostMapping(value = "/oauth/register", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> registrar(@RequestBody String json, HttpServletRequest request) {
        JsonNode corpo = lerJson(json);
        List<String> retornos = new ArrayList<>();
        corpo.path("redirect_uris").forEach(uri -> { if (uri.isTextual()) retornos.add(uri.asText()); });
        Map<String, Object> cliente = oauth.registrarCliente(corpo.path("client_name").asText(null),
                corpo.path("client_uri").asText(null), retornos, ip(request));
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(cliente);
    }

    @PostMapping(value = "/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> token(@RequestParam MultiValueMap<String, String> parametros) {
        Map<String, String> unicos = new HashMap<>();
        parametros.forEach((nome, valores) -> {
            if (valores.size() > 1) throw new OAuthErro("invalid_request", "Parametro repetido: " + nome);
            unicos.put(nome, valores.getFirst());
        });
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache")
                .body(oauth.token(unicos));
    }

    /** RFC 7009: revogar desconecta a conexao do token. */
    @PostMapping(value = "/oauth/revoke", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> revogar(@RequestParam(name = "token", required = false) String token) {
        oauth.revogarToken(token);
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(OAuthErro.class)
    public ResponseEntity<Map<String, Object>> erro(OAuthErro erro) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("error", erro.codigo());
        corpo.put("error_description", erro.getMessage());
        return ResponseEntity.status(erro.status()).cacheControl(CacheControl.noStore()).body(corpo);
    }

    private static ResponseEntity<Map<String, Object>> publico(Map<String, Object> corpo) {
        return ResponseEntity.ok()
                .header("Access-Control-Allow-Origin", "*")
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePublic())
                .body(corpo);
    }

    private JsonNode lerJson(String json) {
        try {
            JsonNode corpo = objectMapper.readTree(json == null ? "" : json);
            if (corpo == null || !corpo.isObject()) throw new OAuthErro("invalid_client_metadata", "Envie um objeto JSON.");
            return corpo;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new OAuthErro("invalid_client_metadata", "JSON invalido.");
        }
    }

    private static String ip(HttpServletRequest request) {
        String encaminhado = request.getHeader("X-Forwarded-For");
        String ip = encaminhado != null && !encaminhado.isBlank() ? encaminhado.split(",")[0].strip() : request.getRemoteAddr();
        return ip == null ? null : ip.length() <= 64 ? ip : ip.substring(0, 64);
    }
}
