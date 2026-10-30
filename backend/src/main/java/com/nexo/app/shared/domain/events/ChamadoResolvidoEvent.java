package com.nexo.app.shared.domain.events;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * O atendimento registrou a solução de um chamado.
 *
 * <p>Aqui o bastão volta ao solicitante, e com prazo: ele tem um número limitado de
 * horas para reabrir se a solução não resolveu. Um chamado resolvido em silêncio é um
 * chamado que fecha sozinho sem ninguém conferir, e é por isso que o
 * {@code prazoReaberturaHoras} viaja no contexto — o texto da regra pode dizer até
 * quando dá para contestar.</p>
 */
public record ChamadoResolvidoEvent(
        UUID idInteracao,
        UUID idChamado,
        String protocolo,
        String assunto,
        String solucao,
        Integer prazoReaberturaHoras,
        UUID idSolicitante,
        UUID idOperador
) implements EventoNotificavel {

    public static final String CHAVE_GATILHO = "chamado.resolvido";

    @Override
    public String chaveGatilho() {
        return CHAVE_GATILHO;
    }

    @Override
    public UUID idReferencia() {
        return idInteracao;
    }

    @Override
    public Map<String, Object> contexto() {
        Map<String, Object> contexto = new HashMap<>();
        contexto.put("protocolo", protocolo);
        contexto.put("assunto", assunto);
        contexto.put("solucao", solucao);
        contexto.put("prazoReaberturaHoras", prazoReaberturaHoras);
        contexto.put("idChamado", idChamado);
        return contexto;
    }

    @Override
    public Map<String, Set<UUID>> papeis() {
        if (idSolicitante == null || idSolicitante.equals(idOperador)) {
            return Map.of();
        }
        return Map.of("solicitante", Set.of(idSolicitante));
    }
}
