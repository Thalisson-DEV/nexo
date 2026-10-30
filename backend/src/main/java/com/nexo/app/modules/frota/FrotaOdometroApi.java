package com.nexo.app.modules.frota;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta pública do livro-razão do odômetro.
 *
 * <p>Checklist, abastecimento, manutenção e telemetria registram KM por aqui. A chamada
 * é idempotente por {@code (origem, idOrigem)}: reenviar o mesmo registro devolve a
 * leitura já gravada. Leitura incoerente é aceita e marcada, nunca recusada — quem
 * decide é o gestor.</p>
 */
public interface FrotaOdometroApi {

    enum OrigemLeitura { CHECKLIST, ABASTECIMENTO, MANUTENCAO, TELEMETRIA, IMPORTACAO }

    record ResultadoLeitura(UUID idLeitura, boolean valida, String motivoInconsistencia, Long kmAtual) {
    }

    ResultadoLeitura registrarLeitura(UUID idVeiculo, long km, OffsetDateTime lidoEm,
                                      OrigemLeitura origem, UUID idOrigem, UUID idUsuario);

    /** Maior KM entre as leituras válidas do veículo. */
    Optional<Long> kmAtual(UUID idVeiculo);
}
