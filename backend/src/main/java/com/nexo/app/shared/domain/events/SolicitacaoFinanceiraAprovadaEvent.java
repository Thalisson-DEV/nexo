package com.nexo.app.shared.domain.events;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A solicitação financeira foi autorizada.
 *
 * <p>Autorizar não conclui nada por si só: um pagamento aprovado ainda precisa ter as
 * parcelas baixadas por quem tem {@code financeiro:pagamento:baixar}. O evento serve,
 * portanto, aos dois lados — confirma a quem pediu e convoca quem executa —, e quem
 * decide qual desses dois avisos existe é a regra, não este código.</p>
 *
 * <p>O {@code valorAprovado} pode ser menor que o solicitado; mandá-lo no contexto
 * evita que a pessoa comemore um valor que não foi o autorizado.</p>
 */
public record SolicitacaoFinanceiraAprovadaEvent(
        UUID idSolicitacao,
        String tipo,
        Long numero,
        BigDecimal valorAprovado,
        String descricao,
        String observacao,
        UUID idSolicitante,
        UUID idAprovador,
        boolean obraCivil
) implements EventoNotificavel {

    public static final String CHAVE_GATILHO = "financeiro.solicitacao_aprovada";

    public SolicitacaoFinanceiraAprovadaEvent(
            UUID idSolicitacao,
            String tipo,
            Long numero,
            BigDecimal valorAprovado,
            String descricao,
            String observacao,
            UUID idSolicitante,
            UUID idAprovador
    ) {
        this(idSolicitacao, tipo, numero, valorAprovado, descricao, observacao, idSolicitante, idAprovador, false);
    }

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
        contexto.put("valorAprovado", valorAprovado);
        contexto.put("descricao", descricao);
        contexto.put("observacao", observacao);
        contexto.put("idSolicitacao", idSolicitacao);
        contexto.put("obraCivil", obraCivil);
        return contexto;
    }

    @Override
    public Map<String, Set<UUID>> papeis() {
        if (idSolicitante == null || idSolicitante.equals(idAprovador)) {
            return Map.of();
        }
        return Map.of("solicitante", Set.of(idSolicitante));
    }
}
