package com.nexo.app.modules.automacao.application.nos;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecCampo;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecNo;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecParam;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecSaida;
import com.nexo.app.modules.automacao.application.motor.Catalogo.ProvedorNos;
import com.nexo.app.modules.automacao.application.motor.ContextoAcao;
import com.nexo.app.modules.automacao.application.motor.ErroAcao;
import com.nexo.app.modules.automacao.domain.expressao.Valores;

/**
 * Chamada HTTP de saída (webhook, Teams, sistemas parceiros). Só {@code https}, nunca para
 * endereços internos (loopback, rede privada, link-local), com tempo limite e resposta
 * limitada — uma automação não pode virar porta para a rede do servidor.
 */
@Component
public class NosIntegracao implements ProvedorNos {

    private static final int MAX_RESPOSTA = 1_000_000;
    private static final Set<Integer> TRANSITORIOS = Set.of(408, 425, 429, 500, 502, 503, 504);
    private final HttpClient cliente = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public Collection<EspecNo> nos() {
        return List.of(EspecNo.de("http.request", "integration", "Chamar um endereço (HTTP)",
                        "Envia uma requisição HTTPS para outro sistema (ex.: webhook do Teams) e devolve a resposta.", "globe")
                .params(EspecParam.de("method", "Método", "select").padrao("POST").estatico()
                                .opcoes(List.of(List.of("GET", "GET"), List.of("POST", "POST"), List.of("PUT", "PUT"),
                                        List.of("PATCH", "PATCH"), List.of("DELETE", "DELETE"))).criar(),
                        EspecParam.de("url", "Endereço (https://…)", "text").obrigatorio().criar(),
                        EspecParam.de("headers", "Cabeçalhos", "keyvalue").padrao(List.of())
                                .campos(EspecCampo.texto("key", "Nome", "Content-Type"), EspecCampo.expressao("value", "Valor", "application/json")).criar(),
                        EspecParam.de("body", "Corpo", "textarea").placeholder("{\"texto\": \"...\"}").criar(),
                        EspecParam.de("timeout", "Tempo limite (segundos)", "number").padrao(30).estatico().faixa(1, 120).avancado().criar())
                .saidas(EspecSaida.de("status", "Código de resposta", "number"), EspecSaida.de("body", "Resposta (texto)", "string"),
                        EspecSaida.de("json", "Resposta (dados)", "any"), EspecSaida.de("headers", "Cabeçalhos da resposta", "object"))
                .executor(this::chamar)
                .efeitoExterno()
                .repeticao("exponential", 3, 10)
                .palavras("http", "api", "webhook", "teams", "integração", "rest")
                .criar());
    }

    private Map<String, Object> chamar(ContextoAcao ctx, Map<String, Object> p) {
        URI uri;
        try {
            uri = URI.create(Valores.texto(p.get("url")).strip());
        } catch (IllegalArgumentException e) {
            throw new ErroAcao("Endereço inválido");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) throw new ErroAcao("Use um endereço https://");
        try {
            for (InetAddress endereco : InetAddress.getAllByName(uri.getHost())) {
                if (endereco.isLoopbackAddress() || endereco.isSiteLocalAddress() || endereco.isLinkLocalAddress()
                        || endereco.isAnyLocalAddress() || endereco.isMulticastAddress()) {
                    throw new ErroAcao("Endereços internos não são permitidos");
                }
            }
        } catch (UnknownHostException e) {
            throw new ErroAcao("Não encontrei o servidor " + uri.getHost(), true);
        }
        String metodo = Valores.texto(p.getOrDefault("method", "POST")).toUpperCase();
        String corpo = Valores.texto(p.get("body"));
        HttpRequest.Builder req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds((long) Math.max(1, Math.min(120, Valores.dbl(p.getOrDefault("timeout", 30))))));
        boolean temTipo = false;
        for (Object h : Valores.lista(p.get("headers"))) {
            Map<String, Object> m = Valores.mapa(h);
            String nome = Valores.texto(m.get("key")).strip();
            if (nome.isEmpty()) continue;
            if (nome.equalsIgnoreCase("content-type")) temTipo = true;
            req.header(nome, Valores.texto(m.get("value")));
        }
        if (!temTipo && !corpo.isBlank()) req.header("Content-Type", corpo.strip().startsWith("{") || corpo.strip().startsWith("[") ? "application/json" : "text/plain");
        req.method(metodo, metodo.equals("GET") || corpo.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(corpo));
        HttpResponse<byte[]> resposta;
        try {
            resposta = cliente.send(req.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new ErroAcao("Falha de rede: " + e.getClass().getSimpleName(), true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ErroAcao("Chamada interrompida", true);
        }
        byte[] bytes = resposta.body();
        String texto = new String(bytes, 0, Math.min(bytes.length, MAX_RESPOSTA), java.nio.charset.StandardCharsets.UTF_8);
        if (resposta.statusCode() >= 400) {
            throw new ErroAcao("O servidor respondeu " + resposta.statusCode() + ": " + (texto.length() > 300 ? texto.substring(0, 300) : texto),
                    TRANSITORIOS.contains(resposta.statusCode()));
        }
        Object json = null;
        if (texto.strip().startsWith("{") || texto.strip().startsWith("[")) {
            try {
                json = Valores.json().readValue(texto, Object.class);
            } catch (JsonProcessingException ignored) {
                json = null;
            }
        }
        Map<String, Object> cabecalhos = new LinkedHashMap<>();
        resposta.headers().map().forEach((k, v) -> cabecalhos.put(k, v.isEmpty() ? "" : String.join(", ", new ArrayList<>(v))));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", resposta.statusCode());
        r.put("body", texto);
        r.put("json", json);
        r.put("headers", cabecalhos);
        return r;
    }
}
