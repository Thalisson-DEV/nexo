package com.nexo.app.shared.domain.events;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Uma requisição de material foi aprovada e está liberada para separação.
 *
 * <p>Dois públicos esperam por isso: o requisitante, que quer saber se pode contar com
 * o material, e o almoxarifado, que só então pode separar. Como o segundo não está
 * escrito no registro, ele é endereçado por permissão na regra.</p>
 *
 * <p>Uma ressalva conhecida: o domínio aceita reaprovar uma requisição já aprovada
 * (é como itens são ajustados no balcão). Como a referência é a própria requisição, a
 * idempotência do motor trata a segunda aprovação como repetição e não notifica de
 * novo — comportamento desejado, já que o fato relevante ("está liberada") não mudou.</p>
 */
public record SolicitacaoMaterialAprovadaEvent(
        UUID idSolicitacao,
        String estoqueOrigem,
        String estoqueDestino,
        Integer totalItens,
        UUID idRequisitante,
        UUID idAprovador
) implements EventoNotificavel {

    public static final String CHAVE_GATILHO = "estoque.material_aprovado";

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
        contexto.put("totalItens", totalItens);
        contexto.put("idSolicitacao", idSolicitacao);
        return contexto;
    }

    @Override
    public Map<String, Set<UUID>> papeis() {
        if (idRequisitante == null || idRequisitante.equals(idAprovador)) {
            return Map.of();
        }
        return Map.of("requisitante", Set.of(idRequisitante));
    }
}
