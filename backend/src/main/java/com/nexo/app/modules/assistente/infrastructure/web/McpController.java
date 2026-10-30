package com.nexo.app.modules.assistente.infrastructure.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexo.app.modules.assistente.application.AssistantSecurityContextFactory;
import com.nexo.app.modules.assistente.application.conexoes.ConexaoIaService;
import com.nexo.app.modules.assistente.application.mcp.McpServidor;
import com.nexo.app.modules.assistente.application.mcp.SessaoMcp;
import com.nexo.app.shared.application.analytics.SemanticQueryContext;
import com.nexo.app.shared.infrastructure.security.AcessoExterno;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Endpoint MCP (Streamable HTTP, sem sessao): cada POST traz uma mensagem JSON-RPC e recebe a resposta
 * em JSON. Sem credencial valida responde 401 apontando os metadados do OAuth, que e como o Claude e o
 * ChatGPT descobrem onde a pessoa deve entrar e autorizar.
 */
@RestController
@RequestMapping("/mcp")
public class McpController {

    private static final long MAX_CORPO = 1_000_000;

    private final McpServidor servidor;
    private final AssistantSecurityContextFactory seguranca;
    private final UrlPublicaIa urls;
    private final ObjectMapper objectMapper;
    private final Set<String> origensPermitidas;

    public McpController(McpServidor servidor, AssistantSecurityContextFactory seguranca, UrlPublicaIa urls,
            ObjectMapper objectMapper, @Value("${ces.cors.allowed-origins:}") String origens) {
        this.servidor = servidor;
        this.seguranca = seguranca;
        this.urls = urls;
        this.objectMapper = objectMapper;
        this.origensPermitidas = Arrays.stream(origens.split(",")).map(String::strip).filter(o -> !o.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> receber(@RequestBody(required = false) String corpo, HttpServletRequest request) {
        if (!origemPermitida(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(json(servidor.erro(null, -32600, "Origem nao permitida.")));
        }
        Optional<AcessoExterno> acesso = acessoAtual();
        if (acesso.isEmpty()) return naoAutorizado(request, "Conecte o Nexo pela sua conta para continuar.");
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        boolean podeConectar = autenticacao.getAuthorities().stream()
                .anyMatch(a -> ConexaoIaService.PERMISSAO_USAR.equals(a.getAuthority()));
        if (!podeConectar) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(json(servidor.erro(null, -32001,
                    "O perfil desta pessoa no Nexo nao permite conectar IAs externas. Fale com o administrador.")));
        }
        if (corpo == null || corpo.isBlank() || corpo.length() > MAX_CORPO) {
            return ResponseEntity.badRequest().body(json(servidor.erro(null, -32600, "Corpo da requisicao vazio ou grande demais.")));
        }
        JsonNode mensagem;
        try {
            mensagem = objectMapper.readTree(corpo);
        } catch (JsonProcessingException e) {
            return ResponseEntity.badRequest().body(json(servidor.erro(null, -32700, "JSON invalido.")));
        }
        SemanticQueryContext contexto = seguranca.current(acesso.get().usuarioId());
        SessaoMcp sessao = new SessaoMcp(acesso.get(), contexto, seguranca.nomeUsuario());

        if (mensagem.isArray()) {
            ArrayNode respostas = objectMapper.createArrayNode();
            for (JsonNode item : mensagem) servidor.processar(item, sessao).ifPresent(respostas::add);
            return respostas.isEmpty() ? ResponseEntity.accepted().build() : ResponseEntity.ok(json(respostas));
        }
        Optional<ObjectNode> resposta = servidor.processar(mensagem, sessao);
        return resposta.map(r -> ResponseEntity.ok(json(r))).orElseGet(() -> ResponseEntity.accepted().build());
    }

    /** Sem fluxo SSE do servidor: as respostas vem no proprio POST. */
    @GetMapping
    public ResponseEntity<Void> fluxo() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).header(HttpHeaders.ALLOW, "POST").build();
    }

    @DeleteMapping
    public ResponseEntity<Void> encerrar() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).header(HttpHeaders.ALLOW, "POST").build();
    }

    private ResponseEntity<String> naoAutorizado(HttpServletRequest request, String mensagem) {
        String desafio = "Bearer resource_metadata=\"" + urls.metadadosRecurso(request) + "\"";
        if (request.getHeader(HttpHeaders.AUTHORIZATION) != null) {
            desafio += ", error=\"invalid_token\", error_description=\"Credencial invalida, vencida ou desconectada\"";
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, desafio)
                .body(json(servidor.erro(JsonNodeFactory.instance.nullNode(), -32001, mensagem)));
    }

    /** Protecao contra DNS rebinding pedida pelo MCP: navegador de outra origem nao chama o servidor. */
    private boolean origemPermitida(HttpServletRequest request) {
        String origem = request.getHeader(HttpHeaders.ORIGIN);
        if (origem == null || origem.isBlank() || "null".equals(origem)) return true;
        List<String> aceitas = new ArrayList<>(origensPermitidas);
        aceitas.add(urls.base(request));
        return aceitas.contains(origem.strip());
    }

    /** Serializado aqui: o conversor HTTP do Spring usa outra versao do Jackson. */
    private String json(JsonNode no) {
        try {
            return objectMapper.writeValueAsString(no);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar resposta MCP", e);
        }
    }

    private static Optional<AcessoExterno> acessoAtual() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null || !(autenticacao.getDetails() instanceof AcessoExterno acesso)) return Optional.empty();
        return Optional.of(acesso);
    }
}
