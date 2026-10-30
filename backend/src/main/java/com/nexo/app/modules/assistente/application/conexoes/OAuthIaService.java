package com.nexo.app.modules.assistente.application.conexoes;

import com.nexo.app.modules.assistente.application.dto.AutorizacaoIaPreviaResponse;
import com.nexo.app.modules.assistente.application.dto.AutorizacaoIaRedirecionamento;
import com.nexo.app.modules.assistente.application.dto.AutorizarIaRequest;
import com.nexo.app.modules.assistente.domain.ClienteConexaoIa;
import com.nexo.app.modules.assistente.domain.CodigoAutorizacaoIa;
import com.nexo.app.modules.assistente.domain.ConexaoIa;
import com.nexo.app.modules.assistente.domain.CredencialConexaoIa;
import com.nexo.app.modules.assistente.domain.TipoCredencialIa;
import com.nexo.app.modules.assistente.infrastructure.persistence.ClienteConexaoIaRepository;
import com.nexo.app.modules.assistente.infrastructure.persistence.CodigoAutorizacaoIaRepository;
import com.nexo.app.modules.assistente.infrastructure.persistence.ConexaoIaRepository;
import com.nexo.app.modules.assistente.infrastructure.persistence.CredencialConexaoIaRepository;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Servidor de autorizacao OAuth 2.1 das conexoes de IA: cadastro dinamico do aplicativo (RFC 7591),
 * codigo de autorizacao com PKCE S256 emitido pela tela de consentimento do Nexo, troca por token e
 * renovacao com rotacao. Escopos: {@value #ESCOPO_LEITURA} e {@value #ESCOPO_ESCRITA}.
 */
@Service
public class OAuthIaService {

    public static final String ESCOPO_LEITURA = "ces.leitura";
    public static final String ESCOPO_ESCRITA = "ces.escrita";
    public static final Duration VIDA_ACESSO = Duration.ofHours(1);
    public static final Duration VIDA_RENOVACAO = Duration.ofDays(60);
    public static final Duration VIDA_CODIGO = Duration.ofMinutes(5);
    static final Duration TOLERANCIA_REUSO = Duration.ofSeconds(60);
    private static final int MAX_CADASTROS_POR_IP_HORA = 30;
    private static final int MAX_ENDERECOS = 10;
    private static final Set<String> ESQUEMAS_PROIBIDOS = Set.of("javascript", "data", "file", "vbscript", "about", "blob");

    private final ClienteConexaoIaRepository clientes;
    private final CodigoAutorizacaoIaRepository codigos;
    private final ConexaoIaRepository conexoes;
    private final CredencialConexaoIaRepository credenciais;

    public OAuthIaService(ClienteConexaoIaRepository clientes, CodigoAutorizacaoIaRepository codigos,
            ConexaoIaRepository conexoes, CredencialConexaoIaRepository credenciais) {
        this.clientes = clientes;
        this.codigos = codigos;
        this.conexoes = conexoes;
        this.credenciais = credenciais;
    }

    /** Cadastro dinamico (RFC 7591). O aplicativo e sempre cliente publico, sem segredo. */
    @Transactional
    public Map<String, Object> registrarCliente(String nome, String site, List<String> redirectUris, String ip) {
        if (redirectUris == null || redirectUris.isEmpty()) {
            throw new OAuthErro("invalid_redirect_uri", "Informe redirect_uris.");
        }
        if (redirectUris.size() > MAX_ENDERECOS) {
            throw new OAuthErro("invalid_redirect_uri", "No maximo " + MAX_ENDERECOS + " redirect_uris.");
        }
        redirectUris.forEach(OAuthIaService::validarRetorno);
        if (ip != null && clientes.countByIpRegistroAndDataCriacaoAfter(ip, OffsetDateTime.now().minusHours(1))
                >= MAX_CADASTROS_POR_IP_HORA) {
            throw new OAuthErro("temporarily_unavailable", "Muitos cadastros em pouco tempo. Tente mais tarde.",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
        ClienteConexaoIa cliente = clientes.save(new ClienteConexaoIa(CredenciaisIa.gerarClientId(), nome,
                List.copyOf(redirectUris), site, ip));
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("client_id", cliente.getClientId());
        resposta.put("client_id_issued_at", cliente.getDataCriacao() == null
                ? OffsetDateTime.now().toEpochSecond() : cliente.getDataCriacao().toEpochSecond());
        resposta.put("client_name", cliente.getNome());
        if (cliente.getSite() != null) resposta.put("client_uri", cliente.getSite());
        resposta.put("redirect_uris", cliente.enderecosDeRetorno());
        resposta.put("grant_types", List.of("authorization_code", "refresh_token"));
        resposta.put("response_types", List.of("code"));
        resposta.put("token_endpoint_auth_method", "none");
        resposta.put("scope", ESCOPO_LEITURA + " " + ESCOPO_ESCRITA);
        return resposta;
    }

    /** O que a tela de consentimento mostra. Pedido invalido nunca volta ao aplicativo: a tela explica. */
    @Transactional(readOnly = true)
    public AutorizacaoIaPreviaResponse previa(AutorizarIaRequest pedido, boolean perfilPodeEscrever) {
        ClienteConexaoIa cliente = validarPedido(pedido);
        return new AutorizacaoIaPreviaResponse(cliente.getNome(), cliente.getSite(), host(pedido.redirectUri()),
                perfilPodeEscrever && pedeEscrita(pedido.scope()));
    }

    /** A pessoa permitiu: emite o codigo de uso unico e devolve o endereco de retorno do aplicativo. */
    @Transactional
    public AutorizacaoIaRedirecionamento autorizar(AutorizarIaRequest pedido, String emissor) {
        ClienteConexaoIa cliente = validarPedido(pedido);
        UUID usuario = Objects.requireNonNull(SecurityUtils.getUsuarioLogadoId(), "Usuario autenticado obrigatorio");
        String codigo = CredenciaisIa.gerar(CredenciaisIa.PREFIXO_CODIGO);
        boolean escrita = pedido.permiteEscrita() && pedeEscrita(pedido.scope());
        codigos.save(new CodigoAutorizacaoIa(CredenciaisIa.hash(codigo), cliente.getClientId(), usuario, escrita,
                pedido.redirectUri(), pedido.codeChallenge(), limitar(pedido.resource(), 500),
                OffsetDateTime.now().plus(VIDA_CODIGO)));
        UriComponentsBuilder retorno = UriComponentsBuilder.fromUriString(pedido.redirectUri())
                .queryParam("code", codigo);
        if (pedido.state() != null) retorno.queryParam("state", pedido.state());
        if (emissor != null) retorno.queryParam("iss", emissor);
        return new AutorizacaoIaRedirecionamento(retorno.encode().build().toUriString());
    }

    /** A pessoa recusou: o aplicativo recebe access_denied (RFC 6749 §4.1.2.1). */
    @Transactional(readOnly = true)
    public AutorizacaoIaRedirecionamento recusar(AutorizarIaRequest pedido, String emissor) {
        validarPedido(pedido);
        UriComponentsBuilder retorno = UriComponentsBuilder.fromUriString(pedido.redirectUri())
                .queryParam("error", "access_denied")
                .queryParam("error_description", "A pessoa nao autorizou a conexao.");
        if (pedido.state() != null) retorno.queryParam("state", pedido.state());
        if (emissor != null) retorno.queryParam("iss", emissor);
        return new AutorizacaoIaRedirecionamento(retorno.encode().build().toUriString());
    }

    /**
     * Endpoint de token: authorization_code (com PKCE) e refresh_token (com rotacao). Recusas tambem gravam:
     * o codigo queima na primeira tentativa e o reuso de um token de renovacao ja trocado desconecta.
     */
    @Transactional(noRollbackFor = OAuthErro.class)
    public Map<String, Object> token(Map<String, String> parametros) {
        String tipo = parametros.get("grant_type");
        if ("authorization_code".equals(tipo)) return trocarCodigo(parametros);
        if ("refresh_token".equals(tipo)) return renovar(parametros);
        throw new OAuthErro("unsupported_grant_type", "Use authorization_code ou refresh_token.");
    }

    /** Revogacao (RFC 7009): desconecta a conexao do token. Token desconhecido nao e erro. */
    @Transactional
    public void revogarToken(String token) {
        if (token == null || token.isBlank()) return;
        credenciais.findByHash(CredenciaisIa.hash(token.strip()))
                .flatMap(credencial -> conexoes.findForUpdate(credencial.getConexaoId()))
                .ifPresent(conexao -> conexao.revogar(conexao.getUsuarioId()));
    }

    private Map<String, Object> trocarCodigo(Map<String, String> parametros) {
        String codigoInformado = obrigatorio(parametros, "code");
        CodigoAutorizacaoIa codigo = codigos.findByHashForUpdate(CredenciaisIa.hash(codigoInformado))
                .orElseThrow(() -> new OAuthErro("invalid_grant", "Codigo de autorizacao invalido."));
        OffsetDateTime agora = OffsetDateTime.now();
        if (!codigo.isUtilizavel(agora)) {
            throw new OAuthErro("invalid_grant", "Codigo de autorizacao vencido ou ja usado.");
        }
        codigo.marcarUsado();
        String clientId = parametros.get("client_id");
        if (clientId != null && !clientId.equals(codigo.getClientId())) {
            throw new OAuthErro("invalid_grant", "O codigo foi emitido para outro aplicativo.");
        }
        if (!codigo.getRedirectUri().equals(parametros.get("redirect_uri"))) {
            throw new OAuthErro("invalid_grant", "redirect_uri diferente do usado na autorizacao.");
        }
        if (!codigo.confereVerificador(parametros.get("code_verifier"))) {
            throw new OAuthErro("invalid_grant", "code_verifier nao confere (PKCE).");
        }
        ClienteConexaoIa cliente = clientes.findByClientIdAndAtivoTrue(codigo.getClientId())
                .orElseThrow(() -> new OAuthErro("invalid_client", "Aplicativo nao cadastrado.", HttpStatus.UNAUTHORIZED));
        ConexaoIa conexao = conexoes.save(ConexaoIa.porLogin(codigo.getUsuarioId(), cliente, codigo.isPermiteEscrita()));
        return emitir(conexao);
    }

    private Map<String, Object> renovar(Map<String, String> parametros) {
        String token = obrigatorio(parametros, "refresh_token");
        CredencialConexaoIa credencial = credenciais.findByHashForUpdate(CredenciaisIa.hash(token))
                .filter(c -> c.getTipo() == TipoCredencialIa.RENOVACAO)
                .orElseThrow(() -> new OAuthErro("invalid_grant", "Token de renovacao invalido."));
        OffsetDateTime agora = OffsetDateTime.now();
        if (credencial.getUsadaEm() != null) {
            // Reuso de token ja trocado: alguem guardou uma copia. Corta a conexao inteira (OAuth 2.1, 4.3.1).
            // Dentro da tolerancia e so recusa: aplicativos as vezes repetem a renovacao em paralelo.
            if (credencial.getUsadaEm().isBefore(agora.minus(TOLERANCIA_REUSO))) {
                conexoes.findForUpdate(credencial.getConexaoId()).ifPresent(conexao -> conexao.revogar(conexao.getUsuarioId()));
            }
            throw new OAuthErro("invalid_grant", "Token de renovacao ja usado. Conecte de novo pelo Nexo.");
        }
        if (!credencial.isValida(agora)) {
            throw new OAuthErro("invalid_grant", "Token de renovacao vencido.");
        }
        ConexaoIa conexao = conexoes.findByIdAndAtivoTrue(credencial.getConexaoId())
                .filter(c -> c.isUtilizavel(agora))
                .orElseThrow(() -> new OAuthErro("invalid_grant", "A conexao foi desconectada no Nexo."));
        String clientId = parametros.get("client_id");
        if (clientId != null && conexao.getClientId() != null && !clientId.equals(conexao.getClientId())) {
            throw new OAuthErro("invalid_grant", "O token foi emitido para outro aplicativo.");
        }
        credencial.marcarUsada();
        return emitir(conexao);
    }

    private Map<String, Object> emitir(ConexaoIa conexao) {
        OffsetDateTime agora = OffsetDateTime.now();
        String acesso = CredenciaisIa.gerar(CredenciaisIa.PREFIXO_ACESSO);
        String renovacao = CredenciaisIa.gerar(CredenciaisIa.PREFIXO_RENOVACAO);
        credenciais.save(new CredencialConexaoIa(conexao.getId(), TipoCredencialIa.ACESSO, CredenciaisIa.hash(acesso),
                agora.plus(VIDA_ACESSO)));
        credenciais.save(new CredencialConexaoIa(conexao.getId(), TipoCredencialIa.RENOVACAO,
                CredenciaisIa.hash(renovacao), agora.plus(VIDA_RENOVACAO)));
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("access_token", acesso);
        resposta.put("token_type", "Bearer");
        resposta.put("expires_in", VIDA_ACESSO.toSeconds());
        resposta.put("refresh_token", renovacao);
        resposta.put("scope", conexao.isPermiteEscrita() ? ESCOPO_LEITURA + " " + ESCOPO_ESCRITA : ESCOPO_LEITURA);
        return resposta;
    }

    private ClienteConexaoIa validarPedido(AutorizarIaRequest pedido) {
        ClienteConexaoIa cliente = clientes.findByClientIdAndAtivoTrue(pedido.clientId())
                .orElseThrow(() -> new OAuthErro("invalid_client",
                        "Este aplicativo não está cadastrado. Volte para a sua IA e comece a conexão de novo."));
        if (!cliente.aceitaRetorno(pedido.redirectUri())) {
            throw new OAuthErro("invalid_request",
                    "O endereço de retorno não confere com o cadastro do aplicativo. Comece a conexão de novo pela sua IA.");
        }
        if (!"code".equals(pedido.responseType())) {
            throw new OAuthErro("unsupported_response_type", "O aplicativo pediu um tipo de autorização que o Nexo não usa.");
        }
        if (pedido.codeChallenge() == null || pedido.codeChallenge().isBlank()
                || !"S256".equals(pedido.codeChallengeMethod())) {
            throw new OAuthErro("invalid_request", "O aplicativo não enviou a proteção PKCE (S256) exigida pelo Nexo.");
        }
        if (pedido.codeChallenge().length() > 128) {
            throw new OAuthErro("invalid_request", "code_challenge invalido.");
        }
        return cliente;
    }

    /** Sem escopo, vale o que a pessoa escolher na tela; pedido so de leitura nunca vira escrita. */
    static boolean pedeEscrita(String escopo) {
        if (escopo == null || escopo.isBlank()) return true;
        List<String> pedidos = List.of(escopo.trim().split("\\s+"));
        return pedidos.contains(ESCOPO_ESCRITA) || !pedidos.contains(ESCOPO_LEITURA);
    }

    static void validarRetorno(String uri) {
        URI destino;
        try {
            destino = new URI(uri);
        } catch (URISyntaxException | NullPointerException e) {
            throw new OAuthErro("invalid_redirect_uri", "redirect_uri invalido: " + uri);
        }
        String esquema = destino.getScheme() == null ? null : destino.getScheme().toLowerCase(Locale.ROOT);
        if (esquema == null || destino.getFragment() != null || ESQUEMAS_PROIBIDOS.contains(esquema)) {
            throw new OAuthErro("invalid_redirect_uri", "redirect_uri nao permitido: " + uri);
        }
        if ("http".equals(esquema) && !loopback(destino.getHost())) {
            throw new OAuthErro("invalid_redirect_uri", "Use https (http so para localhost): " + uri);
        }
        if (("https".equals(esquema) || "http".equals(esquema)) && (destino.getHost() == null || destino.getHost().isBlank())) {
            throw new OAuthErro("invalid_redirect_uri", "redirect_uri sem host: " + uri);
        }
    }

    private static boolean loopback(String host) {
        return host != null && (host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]")
                || host.equals("::1"));
    }

    private static String host(String uri) {
        try {
            URI destino = new URI(uri);
            return destino.getHost() != null ? destino.getHost() : destino.getScheme();
        } catch (URISyntaxException e) {
            return uri;
        }
    }

    private static String obrigatorio(Map<String, String> parametros, String nome) {
        String valor = parametros.get(nome);
        if (valor == null || valor.isBlank()) throw new OAuthErro("invalid_request", "Informe " + nome + ".");
        return valor.strip();
    }

    private static String limitar(String valor, int maximo) {
        if (valor == null) return null;
        return valor.length() <= maximo ? valor : valor.substring(0, maximo);
    }
}
