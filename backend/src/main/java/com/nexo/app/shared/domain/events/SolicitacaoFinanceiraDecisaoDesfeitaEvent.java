package com.nexo.app.shared.domain.events;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Uma solicitação financeira aprovada ou rejeitada voltou a aguardar decisão.
 *
 * <p>Avisa os dois lados de uma decisão que já tinha sido comunicada: quem pediu,
 * que pode ter agido com base nela, e quem tinha decidido, que pode não ter sido
 * quem desfez. Quem desfez não é avisado do próprio ato.</p>
 *
 * @param decisaoAnterior "Aprovação" ou "Rejeição", pronto para o texto da regra
 */
public record SolicitacaoFinanceiraDecisaoDesfeitaEvent(
        UUID idSolicitacao,
        String tipo,
        Long numero,
        BigDecimal valor,
        String descricao,
        String decisaoAnterior,
        String motivo,
        UUID idSolicitante,
        UUID idDecisorAnterior,
        UUID idResponsavel
) implements EventoNotificavel {

    public static final String CHAVE_GATILHO = "financeiro.decisao_desfeita";

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
        contexto.put("decisaoAnterior", decisaoAnterior);
        contexto.put("motivo", motivo);
        contexto.put("idSolicitacao", idSolicitacao);
        return contexto;
    }

    @Override
    public Map<String, Set<UUID>> papeis() {
        Map<String, Set<UUID>> papeis = new HashMap<>();
        adicionar(papeis, "solicitante", idSolicitante);
        adicionar(papeis, "decisorAnterior", idDecisorAnterior);
        return papeis;
    }

    private void adicionar(Map<String, Set<UUID>> papeis, String papel, UUID usuario) {
        if (usuario != null && !usuario.equals(idResponsavel)) {
            papeis.computeIfAbsent(papel, chave -> new HashSet<>()).add(usuario);
        }
    }
}
