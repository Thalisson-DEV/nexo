package com.nexo.app.modules.assistente.infrastructure.web;

import com.nexo.app.modules.assistente.application.AssistantSecurityContextFactory;
import com.nexo.app.modules.assistente.application.agent.AgentTool;
import com.nexo.app.modules.assistente.application.conexoes.ConexaoIaService;
import com.nexo.app.modules.assistente.application.conexoes.OAuthErro;
import com.nexo.app.modules.assistente.application.conexoes.OAuthIaService;
import com.nexo.app.modules.assistente.application.dto.AutorizacaoIaPreviaResponse;
import com.nexo.app.modules.assistente.application.dto.AutorizacaoIaRedirecionamento;
import com.nexo.app.modules.assistente.application.dto.AutorizarIaRequest;
import com.nexo.app.modules.assistente.application.dto.ChaveIaCriadaResponse;
import com.nexo.app.modules.assistente.application.dto.ConexaoIaInfoResponse;
import com.nexo.app.modules.assistente.application.dto.ConexaoIaResponse;
import com.nexo.app.modules.assistente.application.dto.CriarChaveIaRequest;
import com.nexo.app.modules.assistente.application.mcp.McpCatalogo;
import com.nexo.app.modules.assistente.doc.ConexaoIaControllerDoc;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/assistente/conexoes")
@PreAuthorize("hasAuthority('assistente:conexao:use')")
public class ConexaoIaController implements ConexaoIaControllerDoc {

    private final ConexaoIaService conexoes;
    private final OAuthIaService oauth;
    private final McpCatalogo catalogo;
    private final AssistantSecurityContextFactory seguranca;
    private final UrlPublicaIa urls;

    public ConexaoIaController(ConexaoIaService conexoes, OAuthIaService oauth, McpCatalogo catalogo,
            AssistantSecurityContextFactory seguranca, UrlPublicaIa urls) {
        this.conexoes = conexoes;
        this.oauth = oauth;
        this.catalogo = catalogo;
        this.seguranca = seguranca;
        this.urls = urls;
    }

    @Override
    @GetMapping("/info")
    public ResponseEntity<ConexaoIaInfoResponse> info(HttpServletRequest request) {
        return ResponseEntity.ok(conexoes.info(urls.mcp(request)));
    }

    @Override
    @GetMapping
    public ResponseEntity<List<ConexaoIaResponse>> listar() {
        return ResponseEntity.ok(conexoes.listarMinhas());
    }

    @Override
    @PostMapping("/chaves")
    public ResponseEntity<ChaveIaCriadaResponse> criarChave(@Valid @RequestBody CriarChaveIaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(conexoes.criarChave(request));
    }

    @Override
    @PostMapping("/{id}/desconectar")
    public ResponseEntity<ConexaoIaResponse> desconectar(@PathVariable UUID id) {
        return ResponseEntity.ok(conexoes.desconectar(id));
    }

    @Override
    @GetMapping("/todas")
    @PreAuthorize("hasAuthority('assistente:conexao:manage')")
    public ResponseEntity<PageResponse<ConexaoIaResponse>> listarTodas(@RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "50") int tamanho) {
        return ResponseEntity.ok(conexoes.listarTodas(pagina, tamanho));
    }

    @Override
    @PostMapping("/autorizacao/previa")
    public ResponseEntity<AutorizacaoIaPreviaResponse> previa(@Valid @RequestBody AutorizarIaRequest request) {
        return ResponseEntity.ok(oauth.previa(request, perfilPodeEscrever()));
    }

    @Override
    @PostMapping("/autorizacao/permitir")
    public ResponseEntity<AutorizacaoIaRedirecionamento> permitir(@Valid @RequestBody AutorizarIaRequest request,
            HttpServletRequest http) {
        return ResponseEntity.ok(oauth.autorizar(request, urls.base(http)));
    }

    @Override
    @PostMapping("/autorizacao/recusar")
    public ResponseEntity<AutorizacaoIaRedirecionamento> recusar(@Valid @RequestBody AutorizarIaRequest request,
            HttpServletRequest http) {
        return ResponseEntity.ok(oauth.recusar(request, urls.base(http)));
    }

    /** Pedido de autorizacao invalido: a tela explica em vez de devolver a pessoa ao aplicativo. */
    @ExceptionHandler(OAuthErro.class)
    public ResponseEntity<ProblemDetail> pedidoInvalido(OAuthErro erro) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, erro.getMessage());
        problema.setTitle("Pedido de conexão inválido");
        problema.setProperty("codigo", erro.codigo());
        return ResponseEntity.badRequest().body(problema);
    }

    private boolean perfilPodeEscrever() {
        List<AgentTool> ferramentas = catalogo.disponiveis(seguranca.current(SecurityUtils.getUsuarioLogadoId()), true);
        return ferramentas.stream().anyMatch(McpCatalogo::escreve);
    }
}
