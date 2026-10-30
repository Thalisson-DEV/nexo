package com.nexo.app.modules.assistente.infrastructure.web;

import com.nexo.app.modules.assistente.application.files.ArquivoExtraido;
import com.nexo.app.modules.assistente.application.files.ArquivoParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Leitor de paginas com protecao contra SSRF, portado do {@code OnyxWebCrawler}/{@code ssrf_safe_get} do
 * Onyx: somente http/https, sem credenciais na URL, todos os enderecos resolvidos precisam ser publicos
 * (bloqueia loopback, redes privadas, link-local/metadados de nuvem, CGNAT, multicast e reservados),
 * redirecionamentos seguidos manualmente com revalidacao a cada salto, limites de tamanho e tempo.
 * HTML vira texto legivel (jsoup); PDF e extraido pelo PDFBox.
 */
@Component
public class SafeWebFetcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(SafeWebFetcher.class);
    private static final int MAX_REDIRECTS = 5;
    private static final long MAX_HTML_BYTES = 8L * 1024 * 1024;
    private static final long MAX_PDF_BYTES = 25L * 1024 * 1024;
    private static final int MAX_TEXTO = 400_000;
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; CopilotoCES/1.0; +https://app.exemplo.com.br)";
    private static final Set<String> HOSTS_BLOQUEADOS = Set.of("metadata.google.internal", "metadata.gke.internal",
            "metadata.azure.com", "localhost", "metadata");
    private static final Set<String> TAGS_REMOVIDAS = Set.of("script", "style", "noscript", "svg", "iframe", "form",
            "nav", "footer", "header", "aside", "button", "template", "canvas", "select", "input");
    private static final Set<String> BLOCOS = Set.of("p", "div", "section", "article", "main", "br", "li", "ul", "ol",
            "h1", "h2", "h3", "h4", "h5", "h6", "tr", "table", "pre", "blockquote", "dd", "dt", "figcaption", "hr");

    private final ArquivoParser parser;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public SafeWebFetcher(ArquivoParser parser) {
        this.parser = parser;
    }

    public WebPage abrir(String endereco) {
        URI uri;
        try {
            uri = normalizar(endereco);
        } catch (IllegalArgumentException exception) {
            return WebPage.falha(endereco, exception.getMessage());
        }
        try {
            for (int salto = 0; salto <= MAX_REDIRECTS; salto++) {
                validarDestino(uri);
                HttpResponse<InputStream> response = httpClient.send(HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(15))
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,application/pdf,text/plain;q=0.8,*/*;q=0.5")
                        .header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.6")
                        .GET().build(), HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();
                if (status >= 300 && status < 400) {
                    response.body().close();
                    Optional<String> location = response.headers().firstValue("Location");
                    if (location.isEmpty()) return WebPage.falha(uri.toString(), "redirecionamento sem destino");
                    uri = normalizar(uri.resolve(location.get()).toString());
                    continue;
                }
                if (status >= 400) {
                    response.body().close();
                    boolean cloudflare = response.headers().firstValue("cf-ray").isPresent()
                            || response.headers().firstValue("server").map(s -> s.toLowerCase(Locale.ROOT).startsWith("cloudflare")).orElse(false);
                    String motivo = cloudflare ? "bloqueado por desafio anti-robo (Cloudflare); tente outra fonte"
                            : status == 403 ? "acesso negado (HTTP 403); a pagina provavelmente exige autenticacao"
                            : "o site respondeu HTTP " + status;
                    return WebPage.falha(uri.toString(), motivo);
                }
                String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
                boolean pdf = contentType.contains("application/pdf") || uri.getPath().toLowerCase(Locale.ROOT).endsWith(".pdf");
                byte[] corpo = ler(response.body(), pdf ? MAX_PDF_BYTES : MAX_HTML_BYTES);
                if (corpo == null) return WebPage.falha(uri.toString(), "conteudo maior que o limite permitido");
                if (pdf || (corpo.length > 4 && corpo[0] == '%' && corpo[1] == 'P' && corpo[2] == 'D' && corpo[3] == 'F')) {
                    return pdf(uri.toString(), corpo);
                }
                if (contentType.startsWith("image/") || contentType.startsWith("video/") || contentType.startsWith("audio/")) {
                    return WebPage.falha(uri.toString(), "o endereco aponta para midia, nao para texto");
                }
                String texto = new String(corpo, charset(contentType));
                if (contentType.contains("html") || contentType.contains("xml") || texto.stripLeading().startsWith("<")) {
                    return html(uri.toString(), texto);
                }
                return WebPage.sucesso(uri.toString(), titulo(uri), limitar(texto.strip()));
            }
            return WebPage.falha(uri.toString(), "redirecionamentos demais");
        } catch (SsrfException exception) {
            LOGGER.warn("Protecao SSRF bloqueou acesso a {}", uri.getHost());
            return WebPage.falha(uri.toString(), "bloqueado pela protecao de rede (endereco interno ou nao permitido)");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return WebPage.falha(uri.toString(), "leitura interrompida");
        } catch (IOException | IllegalArgumentException exception) {
            return WebPage.falha(uri.toString(), "erro de rede ao abrir a pagina");
        }
    }

    // ------------------------------------------------------------------------------------ SSRF

    static URI normalizar(String endereco) {
        if (endereco == null || endereco.isBlank()) throw new IllegalArgumentException("URL vazia");
        String valor = endereco.strip();
        // Sem esquema ("www.gov.br/...") assume https; com outro esquema (ftp, file...) a validacao abaixo rejeita.
        if (!valor.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) valor = "https://" + valor;
        URI uri;
        try {
            uri = new URI(valor).normalize();
        } catch (URISyntaxException exception) {
            try {
                URL url = URI.create(valor.replace(" ", "%20")).toURL();
                uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), url.getQuery(), null);
            } catch (Exception inner) {
                throw new IllegalArgumentException("URL invalida");
            }
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) throw new IllegalArgumentException("somente http e https sao permitidos");
        if (uri.getRawUserInfo() != null) throw new IllegalArgumentException("URLs com credenciais nao sao permitidas");
        if (uri.getHost() == null || uri.getHost().isBlank()) throw new IllegalArgumentException("URL sem host");
        return uri;
    }

    static void validarDestino(URI uri) throws SsrfException {
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
        if (HOSTS_BLOQUEADOS.contains(host) || host.endsWith(".localhost") || host.endsWith(".internal")
                || host.endsWith(".local")) {
            throw new SsrfException();
        }
        int porta = uri.getPort();
        if (porta != -1 && porta != 80 && porta != 443 && porta != 8080 && porta != 8443) throw new SsrfException();
        InetAddress[] enderecos;
        try {
            enderecos = InetAddress.getAllByName(host);
        } catch (UnknownHostException exception) {
            throw new SsrfException();
        }
        if (enderecos.length == 0) throw new SsrfException();
        for (InetAddress endereco : enderecos) {
            if (!publico(endereco)) throw new SsrfException();
        }
    }

    static boolean publico(InetAddress endereco) {
        if (endereco.isAnyLocalAddress() || endereco.isLoopbackAddress() || endereco.isLinkLocalAddress()
                || endereco.isSiteLocalAddress() || endereco.isMulticastAddress()) {
            return false;
        }
        byte[] b = endereco.getAddress();
        if (endereco instanceof Inet4Address) {
            int o1 = b[0] & 0xff;
            int o2 = b[1] & 0xff;
            if (o1 == 0 || o1 == 10 || o1 == 127 || o1 >= 224) return false;
            if (o1 == 100 && o2 >= 64 && o2 <= 127) return false; // CGNAT
            if (o1 == 169 && o2 == 254) return false; // link-local / metadados
            if (o1 == 172 && o2 >= 16 && o2 <= 31) return false;
            if (o1 == 192 && o2 == 168) return false;
            if (o1 == 192 && o2 == 0 && (b[2] & 0xff) == 0) return false; // IETF
            if (o1 == 198 && (o2 == 18 || o2 == 19)) return false; // benchmark
            return true;
        }
        if (endereco instanceof Inet6Address) {
            int primeiro = b[0] & 0xff;
            if ((primeiro & 0xfe) == 0xfc) return false; // unique local fc00::/7
            if (primeiro == 0xfe && (b[1] & 0xc0) == 0x80) return false; // fe80::/10
            boolean mapeadoV4 = true;
            for (int i = 0; i < 10; i++) if (b[i] != 0) mapeadoV4 = false;
            if (mapeadoV4 && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) {
                try {
                    return publico(InetAddress.getByAddress(Arrays.copyOfRange(b, 12, 16)));
                } catch (UnknownHostException exception) {
                    return false;
                }
            }
            if (primeiro == 0x20 && (b[1] & 0xff) == 0x01 && (b[2] & 0xff) == 0x0d && (b[3] & 0xff) == 0xb8) return false; // doc
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------------------------ extracao

    private WebPage html(String url, String html) {
        Document document = Jsoup.parse(html, url);
        String titulo = document.title();
        if (titulo == null || titulo.isBlank()) {
            Element og = document.selectFirst("meta[property=og:title]");
            titulo = og == null ? titulo(URI.create(url)) : og.attr("content");
        }
        String publicado = null;
        Element dataPublicacao = document.selectFirst("meta[property=article:published_time], meta[name=date], time[datetime]");
        if (dataPublicacao != null) {
            publicado = dataPublicacao.hasAttr("content") ? dataPublicacao.attr("content") : dataPublicacao.attr("datetime");
        }
        for (String tag : TAGS_REMOVIDAS) document.select(tag).remove();
        document.select("[aria-hidden=true], [hidden], .cookie, .cookies, #cookie-banner").remove();
        Element raiz = Optional.ofNullable(document.selectFirst("main"))
                .or(() -> Optional.ofNullable(document.selectFirst("article")))
                .orElse(document.body());
        if (raiz == null) return WebPage.falha(url, "pagina sem conteudo legivel");
        String texto = textoLegivel(raiz);
        if (texto.length() < 200 && raiz != document.body() && document.body() != null) texto = textoLegivel(document.body());
        if (texto.isBlank()) return WebPage.falha(url, "pagina sem conteudo legivel (pode depender de JavaScript)");
        return new WebPage(url, titulo.strip(), limitar(texto), publicado, true, null);
    }

    private WebPage pdf(String url, byte[] corpo) {
        try {
            ArquivoExtraido extraido = parser.extrair("pagina.pdf", corpo, MAX_TEXTO, 0);
            String texto = Objects.toString(extraido.texto(), "").strip();
            if (texto.isEmpty()) return WebPage.falha(url, "PDF sem texto extraivel");
            Object titulo = extraido.metadados().get("titulo");
            return WebPage.sucesso(url, titulo == null || titulo.toString().isBlank() ? titulo(URI.create(url)) : titulo.toString(), limitar(texto));
        } catch (IOException | RuntimeException exception) {
            return WebPage.falha(url, "nao foi possivel ler o PDF");
        }
    }

    static String textoLegivel(Element raiz) {
        StringBuilder texto = new StringBuilder();
        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node instanceof TextNode textNode) {
                    String parte = textNode.text();
                    if (!parte.isBlank()) {
                        if (!texto.isEmpty() && !Character.isWhitespace(texto.charAt(texto.length() - 1))) texto.append(' ');
                        texto.append(parte.strip());
                    }
                } else if (node instanceof Element element) {
                    String tag = element.normalName();
                    if (tag.matches("h[1-6]")) quebra(texto, 2).append("#".repeat(tag.charAt(1) - '0')).append(' ');
                    else if (tag.equals("li")) quebra(texto, 1).append("- ");
                    else if (BLOCOS.contains(tag)) quebra(texto, 1);
                    else if (tag.equals("td") || tag.equals("th")) texto.append(" | ");
                }
            }

            @Override
            public void tail(Node node, int depth) {
                if (node instanceof Element element && BLOCOS.contains(element.normalName())) quebra(texto, 1);
            }
        }, raiz);
        return texto.toString().replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n").strip();
    }

    private static StringBuilder quebra(StringBuilder texto, int linhas) {
        int existentes = 0;
        for (int i = texto.length() - 1; i >= 0 && texto.charAt(i) == '\n'; i--) existentes++;
        if (texto.isEmpty()) return texto;
        for (int i = existentes; i < linhas; i++) texto.append('\n');
        return texto;
    }

    private static byte[] ler(InputStream input, long maximo) throws IOException {
        try (input) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            long total = 0;
            int lidos;
            while ((lidos = input.read(buffer)) != -1) {
                total += lidos;
                if (total > maximo) return null;
                output.write(buffer, 0, lidos);
            }
            return output.toByteArray();
        }
    }

    private static Charset charset(String contentType) {
        int indice = contentType.indexOf("charset=");
        if (indice >= 0) {
            String nome = contentType.substring(indice + 8).replace("\"", "").split("[;\\s]")[0];
            try {
                return Charset.forName(nome);
            } catch (RuntimeException ignored) {
                // charset desconhecido
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static String titulo(URI uri) {
        String caminho = uri.getPath() == null ? "" : uri.getPath();
        String ultimo = caminho.substring(caminho.lastIndexOf('/') + 1);
        return ultimo.isBlank() ? uri.getHost() : uri.getHost() + " - " + URLDecoder.decode(ultimo, StandardCharsets.UTF_8);
    }

    private static String limitar(String texto) {
        return texto.length() <= MAX_TEXTO ? texto : texto.substring(0, MAX_TEXTO);
    }

    public record WebPage(String url, String titulo, String texto, String publicadoEm, boolean sucesso, String falha) {
        static WebPage sucesso(String url, String titulo, String texto) {
            return new WebPage(url, titulo, texto, null, true, null);
        }

        static WebPage falha(String url, String motivo) {
            return new WebPage(url, "", "", null, false, motivo);
        }
    }

    static final class SsrfException extends IOException {
        SsrfException() {
            super("destino bloqueado");
        }
    }
}
