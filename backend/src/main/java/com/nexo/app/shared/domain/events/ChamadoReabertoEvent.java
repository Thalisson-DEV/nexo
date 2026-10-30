package com.nexo.app.shared.domain.events;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Um chamado resolvido voltou para atendimento.
 *
 * <p>A reabertura desfaz um desfecho, e quem precisa saber disso é quem já tinha o
 * caso por encerrado. Normalmente é o solicitante que reabre, e o responsável que
 * precisa ser avisado; quando quem reabre é o próprio atendimento, o papel se inverte
 * — daí os dois papéis serem preenchidos, sempre menos quem executou a ação.</p>
 *
 * <p>Se o chamado estiver sem responsável (um encaminhamento anterior o removeu), o
 * papel fica vazio e a regra não alcança ninguém. Uma regra adicional endereçando a
 * permissão {@code chamado:manage} cobre esse caso ao custo de avisar a central
 * inteira, e por isso não vem de fábrica.</p>
 */
public record ChamadoReabertoEvent(
        UUID idInteracao,
        UUID idChamado,
        String protocolo,
        String assunto,
        UUID idSolicitante,
        UUID idResponsavel,
        UUID idOperador
) implements EventoNotificavel {

    public static final String CHAVE_GATILHO = "chamado.reaberto";

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
        contexto.put("idChamado", idChamado);
        return contexto;
    }

    @Override
    public Map<String, Set<UUID>> papeis() {
        Map<String, Set<UUID>> papeis = new LinkedHashMap<>();
        if (idResponsavel != null && !idResponsavel.equals(idOperador)) {
            papeis.put("responsavel", Set.of(idResponsavel));
        }
        if (idSolicitante != null && !idSolicitante.equals(idOperador)) {
            papeis.put("solicitante", Set.of(idSolicitante));
        }
        return papeis;
    }
}
