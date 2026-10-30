package com.nexo.app.shared.domain.events;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Um chamado passou a ter dono.
 *
 * <p>É o momento em que o trabalho deixa de ser da fila e passa a ser de alguém. Sem
 * este aviso, a atribuição só chega à pessoa se ela reparar sozinha que um chamado
 * novo apareceu na sua lista — que é exatamente o modo como um chamado atribuído fica
 * parado sem ninguém notar.</p>
 *
 * <p>O {@link #idReferencia()} é o <b>id da interação</b> de atribuição, não o do
 * chamado. Um mesmo chamado é reatribuído quantas vezes for preciso, e a chave de
 * idempotência do motor é derivada daqui: apontar para o chamado faria a segunda
 * atribuição ser descartada como repetição da primeira.</p>
 */
public record ChamadoAtribuidoEvent(
        UUID idInteracao,
        UUID idChamado,
        String protocolo,
        String assunto,
        String prioridade,
        String equipe,
        UUID idResponsavel,
        UUID idOperador
) implements EventoNotificavel {

    public static final String CHAVE_GATILHO = "chamado.atribuido";

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
        contexto.put("prioridade", prioridade);
        contexto.put("equipe", equipe);
        contexto.put("idChamado", idChamado);
        return contexto;
    }

    /**
     * Quem assume o próprio chamado (o atendente que responde e vira responsável no
     * mesmo gesto) não é avisado: já sabe.
     */
    @Override
    public Map<String, Set<UUID>> papeis() {
        if (idResponsavel == null || idResponsavel.equals(idOperador)) {
            return Map.of();
        }
        return Map.of("responsavel", Set.of(idResponsavel));
    }
}
