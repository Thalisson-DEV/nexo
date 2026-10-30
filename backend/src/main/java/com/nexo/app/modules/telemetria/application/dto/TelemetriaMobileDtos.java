package com.nexo.app.modules.telemetria.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Contrato de {@code POST /v1/telemetria/mobile}. Só aceita o que é diagnóstico técnico:
 * tipo do evento, instante e um mapa pequeno de valores primitivos.
 *
 * @author <b>Thalisson</b>
 */
public final class TelemetriaMobileDtos {

    private TelemetriaMobileDtos() {
    }

    /**
     * @param tipo  nome do evento em minúsculas com pontos, ex. {@code retomada.decisao}
     * @param em    instante em que ocorreu no aparelho
     * @param dados até 20 pares chave/valor primitivos; textos são truncados em 80 caracteres
     */
    public record EventoRequest(
            @NotNull @Pattern(regexp = "^[a-z]{1,20}(\\.[a-z]{1,20}){0,2}$") String tipo,
            @NotNull OffsetDateTime em,
            Map<String, Object> dados) {
    }

    /**
     * @param idAparelho identificador aleatório do aparelho (sem vínculo com pessoa)
     * @param versaoApp  hash curto da versão do app
     * @param eventos    de 1 a 100 eventos por lote
     */
    public record LoteRequest(
            @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9-]*$") String idAparelho,
            @Size(max = 40) String versaoApp,
            @NotNull @Size(min = 1, max = 100) List<@Valid @NotNull EventoRequest> eventos) {
    }

    /**
     * @param recebidos  eventos gravados
     * @param descartados eventos fora da janela de tempo aceita
     */
    public record LoteResponse(int recebidos, int descartados) {
    }
}
