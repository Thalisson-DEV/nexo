package com.nexo.app.shared.domain.events;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A solicitação financeira foi recusada, com justificativa.
 *
 * <p>O domínio já exige o motivo para rejeitar; o que faltava era ele chegar a quem
 * precisa agir sobre ele. Uma rejeição silenciosa deixa o solicitante refazendo a
 * mesma solicitação com o mesmo defeito.</p>
 */
public record SolicitacaoFinanceiraRejeitadaEvent(
        UUID idSolicitacao,
        String tipo,
        Long numero,
        BigDecimal valor,
        String descricao,
        String motivoRejeicao,
        UUID idSolicitante,
        UUID idDecisor
) implements EventoNotificavel {

    public static final String CHAVE_GATILHO = "financeiro.solicitacao_rejeitada";

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
        contexto.put("tipo", tipo);
        contexto.put("numero", numero);
        contexto.put("valor", valor);
        contexto.put("descricao", descricao);
        contexto.put("motivoRejeicao", motivoRejeicao);
        contexto.put("idSolicitacao", idSolicitacao);
        return contexto;
    }

    @Override
    public Map<String, Set<UUID>> papeis() {
        if (idSolicitante == null || idSolicitante.equals(idDecisor)) {
            return Map.of();
        }
        return Map.of("solicitante", Set.of(idSolicitante));
    }
}
