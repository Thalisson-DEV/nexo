package com.nexo.app.shared.domain.events;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A tratativa de uma ocorrência de segurança foi encerrada.
 *
 * <p>Fecha o ciclo aberto por {@link EventoSstRegistradoEvent}: quem foi avisado do
 * acidente é quem tem interesse em saber o que se fez a respeito. Como lá, nenhum nome
 * de pessoa viaja no contexto.</p>
 */
public record EventoSstConcluidoEvent(
        UUID idEvento,
        String numeroRegistro,
        String tipo,
        String tipoRotulo,
        String atividade,
        String base,
        String tratativa,
        UUID idGestor,
        Set<UUID> idsTecnicos
) implements EventoNotificavel {

    public static final String CHAVE_GATILHO = "sst.evento.concluido";

    @Override
    public String chaveGatilho() {
        return CHAVE_GATILHO;
    }

    @Override
    public UUID idReferencia() {
        return idEvento;
    }

    @Override
    public Map<String, Object> contexto() {
        Map<String, Object> contexto = new HashMap<>();
        contexto.put("numeroRegistro", numeroRegistro);
        contexto.put("tipo", tipo);
        contexto.put("tipoRotulo", tipoRotulo);
        contexto.put("atividade", atividade);
        contexto.put("base", base);
        contexto.put("tratativa", tratativa);
        contexto.put("idEvento", idEvento);
        return contexto;
    }

    @Override
    public Map<String, Set<UUID>> papeis() {
        return Map.of();
    }
}
