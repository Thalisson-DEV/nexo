package com.nexo.app.shared.domain.events;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Uma solicitação de compra ou pagamento saiu do rascunho e entrou na fila de decisão.
 *
 * <p>Já existe um gatilho por limiar para a fila financeira, mas ele avisa tarde por
 * natureza: só quando o acúmulo atinge o número configurado. Este avisa na hora, que é
 * o que uma solicitação urgente exige.</p>
 *
 * <p>O {@code tipo} viaja no contexto porque compra e pagamento têm aprovadores
 * distintos ({@code financeiro:compra:aprovar} e {@code financeiro:pagamento:aprovar}).
 * Uma condição sobre ele roteia cada caso para a permissão certa, o que evita duplicar
 * o gatilho só para separar dois públicos.</p>
 */
public record SolicitacaoFinanceiraSubmetidaEvent(
        UUID idSolicitacao,
        String tipo,
        Long numero,
        BigDecimal valor,
        String descricao,
        String prioridade,
        UUID idSolicitante,
        String base,
        String uf,
        boolean frota,
        boolean obraCivil
) implements EventoNotificavel {

    public static final String CHAVE_GATILHO = "financeiro.solicitacao_submetida";

    public SolicitacaoFinanceiraSubmetidaEvent(
            UUID idSolicitacao,
            String tipo,
            Long numero,
            BigDecimal valor,
            String descricao,
            String prioridade,
            UUID idSolicitante,
            String base,
            String uf,
            boolean frota
    ) {
        this(idSolicitacao, tipo, numero, valor, descricao, prioridade, idSolicitante, base, uf, frota, false);
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
        contexto.put("valor", valor);
        contexto.put("descricao", descricao);
        contexto.put("prioridade", prioridade);
        contexto.put("idSolicitacao", idSolicitacao);
        contexto.put("base", base);
        contexto.put("uf", uf);
        contexto.put("frota", frota);
        contexto.put("obraCivil", obraCivil);
        return contexto;
    }

    @Override
    public Map<String, Set<UUID>> papeis() {
        return idSolicitante == null
                ? Map.of()
                : Map.of("solicitante", Set.of(idSolicitante));
    }
}
