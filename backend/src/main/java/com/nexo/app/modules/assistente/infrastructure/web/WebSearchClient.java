package com.nexo.app.modules.assistente.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexo.app.modules.assistente.application.AssistenteProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provedores de pesquisa web, portados dos clientes do Onyx
 * ({@code onyx/tools/tool_implementations/web_search/clients}): Serper, Brave, Tavily, Google PSE, Exa e
 * SearXNG. O provedor ativo vem de {@code AI_WEB_SEARCH_PROVIDER}; sem configuracao a ferramenta nao e
 * oferecida ao modelo. Falhas transitorias (429/5xx/rede) sao repetidas com backoff, como no Onyx.
 */
@Component
public class WebSearchClient {

    public enum Provedor { SERPER, BRAVE, TAVILY, GOOGLE_PSE, EXA, SEARXNG }

    private static final int TENTATIVAS = 3;
    private static final Pattern SITE = Pattern.compile("site:\\s*(\\S+)", Pattern.CASE_INSENSITIVE);

    private final AssistenteProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public WebSearchClient(AssistenteProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Optional<Provedor> provedor() {
        String configurado = Objects.toString(properties.getWebSearch().getProvider(), "").trim();
        if (configurado.isEmpty()) return Optional.empty();
        String normalizado = configurado.toUpperCase(Locale.ROOT).replace('-', '_');
        if (normalizado.equals("GOOGLE")) normalizado = "GOOGLE_PSE";
        try {
            return Optional.of(Provedor.valueOf(normalizado));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public boolean configurado() {
        AssistenteProperties.WebSearch config = properties.getWebSearch();
        return provedor().map(provedor -> switch (provedor) {
            case SEARXNG -> naoVazio(config.getSearxngUrl());
            case GOOGLE_PSE -> naoVazio(config.getApiKey()) && naoVazio(config.getGoogleCx());
            default -> naoVazio(config.getApiKey());
        }).orElse(false);
    }

    public List<WebSearchResult> pesquisar(String consulta) throws IOException, InterruptedException {
        Provedor provedor = provedor().orElseThrow(() -> new IOException("Pesquisa web nao configurada"));
        int maximo = Math.max(1, Math.min(20, properties.getWebSearch().getMaxResults()));
        IOException ultima = null;
        for (int tentativa = 1; tentativa <= TENTATIVAS; tentativa++) {
            try {
                List<WebSearchResult> resultados = switch (provedor) {
                    case SERPER -> serper(consulta, maximo);
                    case BRAVE -> brave(consulta, maximo);
                    case TAVILY -> tavily(consulta, maximo);
                    case GOOGLE_PSE -> googlePse(consulta, maximo);
                    case EXA -> exa(consulta, maximo);
                    case SEARXNG -> searxng(consulta, maximo);
                };
                // Mesmo filtro do Onyx: descarta resultados sem titulo e sem trecho.
                return resultados.stream()
                        .filter(resultado -> !resultado.titulo().isBlank() || !resultado.trecho().isBlank())
                        .limit(maximo)
                        .toList();
            } catch (RetentavelException exception) {
                ultima = exception;
                if (tentativa < TENTATIVAS) Thread.sleep(1000L * (1L << (tentativa - 1)));
            }
        }
        throw ultima;
    }

    // ------------------------------------------------------------------------------------ provedores

    private List<WebSearchResult> serper(String consulta, int maximo) throws IOException, InterruptedException {
        ObjectNode corpo = objectMapper.createObjectNode().put("q", consulta).put("num", maximo).put("gl", "br").put("hl", "pt-br");
        JsonNode resposta = enviar(HttpRequest.newBuilder(URI.create("https://google.serper.dev/search"))
                .header("X-API-KEY", apiKey()).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corpo.toString())), "Serper");
        List<WebSearchResult> resultados = new ArrayList<>();
        for (JsonNode item : resposta.path("organic")) {
            String link = texto(item, "link");
            if (link.isEmpty()) continue;
            resultados.add(new WebSearchResult(texto(item, "title"), link, texto(item, "snippet"), textoOuNulo(item, "date")));
        }
        return resultados;
    }

    private List<WebSearchResult> brave(String consulta, int maximo) throws IOException, InterruptedException {
        String url = "https://api.search.brave.com/res/v1/web/search?q=" + encode(consulta) + "&count=" + maximo
                + "&country=BR&search_lang=pt-br";
        JsonNode resposta = enviar(HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/json").header("X-Subscription-Token", apiKey()).GET(), "Brave");
        List<WebSearchResult> resultados = new ArrayList<>();
        for (JsonNode item : resposta.path("web").path("results")) {
            String link = texto(item, "url");
            if (link.isEmpty()) continue;
            resultados.add(new WebSearchResult(texto(item, "title"), link, limparHtml(texto(item, "description")),
                    textoOuNulo(item, "page_age")));
        }
        return resultados;
    }

    private List<WebSearchResult> tavily(String consulta, int maximo) throws IOException, InterruptedException {
        ObjectNode corpo = objectMapper.createObjectNode().put("query", consulta).put("max_results", maximo)
                .put("search_depth", "basic").put("topic", "general");
        JsonNode resposta = enviar(HttpRequest.newBuilder(URI.create("https://api.tavily.com/search"))
                .header("Authorization", "Bearer " + apiKey()).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corpo.toString())), "Tavily");
        List<WebSearchResult> resultados = new ArrayList<>();
        for (JsonNode item : resposta.path("results")) {
            String link = texto(item, "url");
            if (link.isEmpty()) continue;
            resultados.add(new WebSearchResult(texto(item, "title"), link, texto(item, "content"),
                    textoOuNulo(item, "published_date")));
        }
        return resultados;
    }

    private List<WebSearchResult> googlePse(String consulta, int maximo) throws IOException, InterruptedException {
        String url = "https://customsearch.googleapis.com/customsearch/v1?key=" + encode(apiKey()) + "&cx="
                + encode(properties.getWebSearch().getGoogleCx()) + "&q=" + encode(consulta) + "&num=" + Math.min(10, maximo)
                + "&hl=pt-BR&gl=br";
        JsonNode resposta = enviar(HttpRequest.newBuilder(URI.create(url)).GET(), "Google PSE");
        List<WebSearchResult> resultados = new ArrayList<>();
        for (JsonNode item : resposta.path("items")) {
            String link = texto(item, "link");
            if (link.isEmpty()) continue;
            String publicado = null;
            JsonNode metatags = item.path("pagemap").path("metatags");
            if (metatags.isArray() && !metatags.isEmpty()) {
                JsonNode meta = metatags.get(0);
                publicado = textoOuNulo(meta, "article:published_time");
                if (publicado == null) publicado = textoOuNulo(meta, "og:updated_time");
            }
            resultados.add(new WebSearchResult(texto(item, "title"), link, texto(item, "snippet"), publicado));
        }
        return resultados;
    }

    private List<WebSearchResult> exa(String consulta, int maximo) throws IOException, InterruptedException {
        // Como no Onyx: operadores site: viram includeDomains.
        List<String> dominios = new ArrayList<>();
        Matcher matcher = SITE.matcher(consulta);
        while (matcher.find()) dominios.add(matcher.group(1).split("/")[0].replaceFirst("^www\\.", ""));
        String limpa = SITE.matcher(consulta).replaceAll("").trim();
        if (limpa.isEmpty() && !dominios.isEmpty()) limpa = dominios.getFirst();
        ObjectNode corpo = objectMapper.createObjectNode().put("query", limpa).put("type", "auto").put("numResults", maximo);
        corpo.putObject("contents").putObject("highlights").put("numSentences", 2).put("highlightsPerUrl", 1);
        if (!dominios.isEmpty()) {
            var array = corpo.putArray("includeDomains");
            dominios.forEach(array::add);
        }
        JsonNode resposta = enviar(HttpRequest.newBuilder(URI.create("https://api.exa.ai/search"))
                .header("x-api-key", apiKey()).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corpo.toString())), "Exa");
        List<WebSearchResult> resultados = new ArrayList<>();
        for (JsonNode item : resposta.path("results")) {
            String link = texto(item, "url");
            if (link.isEmpty()) continue;
            JsonNode destaques = item.path("highlights");
            String trecho = destaques.isArray() && !destaques.isEmpty() ? destaques.get(0).asText("").trim() : "";
            resultados.add(new WebSearchResult(texto(item, "title"), link, trecho, textoOuNulo(item, "publishedDate")));
        }
        return resultados;
    }

    private List<WebSearchResult> searxng(String consulta, int maximo) throws IOException, InterruptedException {
        String base = properties.getWebSearch().getSearxngUrl().trim();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String corpo = "q=" + encode(consulta) + "&format=json&language=pt-BR";
        JsonNode resposta = enviar(HttpRequest.newBuilder(URI.create(base + "/search"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(corpo)), "SearXNG");
        List<WebSearchResult> resultados = new ArrayList<>();
        for (JsonNode item : resposta.path("results")) {
            if (resultados.size() >= maximo) break;
            String link = texto(item, "url");
            if (link.isEmpty()) continue;
            resultados.add(new WebSearchResult(texto(item, "title"), link, texto(item, "content"),
                    textoOuNulo(item, "publishedDate")));
        }
        return resultados;
    }

    // ------------------------------------------------------------------------------------ http

    private JsonNode enviar(HttpRequest.Builder builder, String nome) throws IOException, InterruptedException {
        HttpResponse<String> response;
        try {
            response = httpClient.send(builder.timeout(Duration.ofSeconds(30)).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new RetentavelException(nome + ": falha de rede (" + exception.getClass().getSimpleName() + ")");
        }
        int status = response.statusCode();
        if (status == 429 || status >= 500) throw new RetentavelException(nome + " respondeu HTTP " + status);
        if (status >= 400) {
            throw new IOException(nome + " recusou a pesquisa (HTTP " + status + "): " + detalheErro(response.body()));
        }
        return objectMapper.readTree(response.body());
    }

    private String detalheErro(String corpo) {
        if (corpo == null || corpo.isBlank()) return "sem detalhes";
        try {
            JsonNode node = objectMapper.readTree(corpo);
            for (String campo : List.of("message", "detail", "error")) {
                JsonNode valor = node.path(campo);
                if (valor.isTextual()) return limitar(valor.asText(), 200);
                if (valor.isObject() && valor.path("message").isTextual()) return limitar(valor.path("message").asText(), 200);
            }
        } catch (IOException ignored) {
            // corpo nao JSON
        }
        return limitar(corpo.strip(), 200);
    }

    private String apiKey() {
        return Objects.toString(properties.getWebSearch().getApiKey(), "").trim();
    }

    private static String texto(JsonNode node, String campo) {
        JsonNode valor = node.path(campo);
        return valor.isTextual() ? valor.asText().trim() : "";
    }

    private static String textoOuNulo(JsonNode node, String campo) {
        String valor = texto(node, campo);
        return valor.isEmpty() ? null : valor;
    }

    private static String limparHtml(String texto) {
        return texto.replaceAll("<[^>]+>", "");
    }

    private static String encode(String valor) {
        return URLEncoder.encode(valor == null ? "" : valor, StandardCharsets.UTF_8);
    }

    private static boolean naoVazio(String valor) {
        return valor != null && !valor.isBlank();
    }

    private static String limitar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo) + "...";
    }

    public record WebSearchResult(String titulo, String url, String trecho, String publicadoEm) {
        public WebSearchResult {
            titulo = titulo == null ? "" : titulo;
            trecho = trecho == null ? "" : trecho;
        }
    }

    private static final class RetentavelException extends IOException {
        RetentavelException(String message) {
            super(message);
        }
    }
}
