package com.nexo.app.shared.domain.events;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Uma requisição de material foi recusada.
 *
 * <p>O domínio exige motivo cadastrado <em>e</em> justificativa livre — os dois viajam
 * no contexto porque servem a propósitos diferentes: o motivo classifica (e cabe numa
 * condição de regra, permitindo tratar certas recusas com prioridade alta), a
 * justificativa explica.</p>
 */
public record SolicitacaoMaterialRejeitadaEvent(
        UUID idSolicitacao,
        String estoqueOrigem,
        String estoqueDestino,
        String motivoRejeicao,
        String justificativa,
        UUID idRequisitante,
        UUID idDecisor
) implements EventoNotificavel {

    public static final String CHAVE_GATILHO = "estoque.material_rejeitado";

    @Override
    public String chaveGatilho() {
        return CHAVE_GATILHO;
    }

    @Override
    public UUID idReferencia() {
        return idSolicitacao;
    }

    @Override
    public Map<String, Object> contexto() {
        Map<String, Object> contexto = new HashMap<>();
        contexto.put("estoqueOrigem", estoqueOrigem);
        contexto.put("estoqueDestino", estoqueDestino);
        contexto.put("motivoRejeicao", motivoRejeicao);
        contexto.put("justificativa", justificativa);
        contexto.put("idSolicitacao", idSolicitacao);
        return contexto;
    }

    @Override
    public Map<String, Set<UUID>> papeis() {
        if (idRequisitante == null || idRequisitante.equals(idDecisor)) {
            return Map.of();
        }
        return Map.of("requisitante", Set.of(idRequisitante));
    }
}
