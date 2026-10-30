package com.nexo.app.modules.obras.infrastructure.rotas;

import com.nexo.app.modules.obras.application.port.RotaRodoviariaPort;
import com.nexo.app.shared.infrastructure.config.CacheNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Adaptador para um servidor OSRM (API {@code /route/v1/driving}). Sem URL configurada fica desligado e a
 * aplicação usa a estimativa. Falhas passam pelo circuit breaker {@code rotas-obras} e viram vazio.
 *
 * <p>O cache usa as coordenadas arredondadas em 4 casas (~11 m), suficiente para base → obra.</p>
 */
@Slf4j
@Component
public class OsrmRotaRodoviariaAdapter implements RotaRodoviariaPort {

    private final RestClient cliente;
    private final CircuitBreakerFactory<?, ?> circuitBreakers;
    private final boolean habilitado;

    public OsrmRotaRodoviariaAdapter(@Value("${ces.obras.rotas.osrm-url:}") String urlBase,
                                     RestClient.Builder builder,
                                     CircuitBreakerFactory<?, ?> circuitBreakers) {
        this.habilitado = urlBase != null && !urlBase.isBlank();
        this.cliente = habilitado ? builder.baseUrl(urlBase).build() : null;
        this.circuitBreakers = circuitBreakers;
    }

    @Override
    @Cacheable(cacheNames = CacheNames.OBRAS_ROTAS,
            key = "T(java.lang.String).format(T(java.util.Locale).ROOT, '%.4f,%.4f;%.4f,%.4f', "
                    + "#origem.latitude(), #origem.longitude(), #destino.latitude(), #destino.longitude())",
            unless = "#result == null")
    public Optional<Rota> calcular(Coordenada origem, Coordenada destino) {
        if (!habilitado) {
            return Optional.empty();
        }
        return circuitBreakers.create("rotas-obras").run(() -> consultar(origem, destino), erro -> {
            log.warn("Motor de rotas indisponível; usando estimativa ({})", erro.getClass().getSimpleName());
            return Optional.empty();
        });
    }

    @SuppressWarnings("unchecked")
    private Optional<Rota> consultar(Coordenada origem, Coordenada destino) {
        String caminho = String.format(Locale.ROOT, "/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=false",
                origem.longitude(), origem.latitude(), destino.longitude(), destino.latitude());
        Map<String, Object> resposta = cliente.get().uri(caminho).retrieve().body(Map.class);
        if (resposta == null || !"Ok".equals(resposta.get("code"))) {
            return Optional.empty();
        }
        List<Map<String, Object>> rotas = (List<Map<String, Object>>) resposta.get("routes");
        if (rotas == null || rotas.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> rota = rotas.getFirst();
        double metros = ((Number) rota.get("distance")).doubleValue();
        double segundos = ((Number) rota.get("duration")).doubleValue();
        return Optional.of(new Rota(
                BigDecimal.valueOf(metros / 1000d).setScale(2, RoundingMode.HALF_UP),
                (int) Math.ceil(segundos / 60d)));
    }
}
