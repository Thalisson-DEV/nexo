package com.nexo.app.modules.obras.application.port;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Porta para um motor de rotas rodoviárias (distância e tempo de carro entre dois pontos).
 *
 * <p>Vazio quando não há motor configurado, ele está fora do ar ou não encontrou rota; quem chama usa a
 * estimativa em linha reta corrigida e marca o resultado como estimado.</p>
 */
public interface RotaRodoviariaPort {

    Optional<Rota> calcular(Coordenada origem, Coordenada destino);

    record Coordenada(double latitude, double longitude) {
    }

    record Rota(BigDecimal distanciaKm, int tempoMinutos) {
    }
}
