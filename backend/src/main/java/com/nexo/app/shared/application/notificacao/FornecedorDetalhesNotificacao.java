package com.nexo.app.shared.application.notificacao;

import java.util.Optional;
import java.util.UUID;

/**
 * Como um módulo entrega ao e-mail a ficha do registro notificado e as ações possíveis.
 *
 * <p>Mesmo desenho de {@link FornecedorAnexosNotificacao}: o e-mail não conhece desconto
 * nem solicitação financeira, e eles não conhecem e-mail. A chave do gatilho liga os
 * dois. A consulta acontece no envio, então a ficha reflete o registro como ele está
 * — e um botão de aprovar não sai num e-mail cuja solicitação já foi decidida.</p>
 */
public interface FornecedorDetalhesNotificacao {

    /** Se este fornecedor responde pelo gatilho informado. */
    boolean atende(String chaveGatilho);

    /**
     * Os detalhes do registro. Vazio quando o registro não existe mais; o e-mail sai
     * então com a ficha genérica.
     */
    Optional<DetalhesNotificacao> detalhesDe(String chaveGatilho, UUID idReferencia);

    /**
     * Se as ações deste gatilho são pessoais — links que só valem para quem recebeu.
     * Nesse caso o e-mail sai um por destinatário, em vez de um só com todos no "Para".
     */
    default boolean acoesPessoais(String chaveGatilho) {
        return false;
    }

    /** Os detalhes para um destinatário específico. Sem ações pessoais, é o mesmo que {@link #detalhesDe}. */
    default Optional<DetalhesNotificacao> detalhesPara(String chaveGatilho, UUID idReferencia, UUID idDestinatario) {
        return detalhesDe(chaveGatilho, idReferencia);
    }
}
