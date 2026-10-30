package com.nexo.app.shared.application.notificacao;

import java.util.List;
import java.util.UUID;

/**
 * Como um módulo entrega ao e-mail os arquivos do registro que ele notificou.
 *
 * <p>Segue o mesmo desenho de {@code ProvedorMetricaGatilho}: o módulo de notificações
 * não conhece desconto nem chamado, e nenhum deles conhece e-mail. O que os liga é a
 * chave do gatilho — quem publica {@code desconto.solicitado} é quem sabe achar os
 * anexos daquele desconto.</p>
 *
 * <p>A consulta acontece <b>no momento do envio</b>, e não quando o evento foi
 * publicado. É o que faz o e-mail levar os arquivos que existem quando ele sai, e não
 * os que existiam no instante do commit — em vários fluxos do Nexo o registro nasce
 * primeiro e os anexos sobem logo depois, por outra chamada.</p>
 */
public interface FornecedorAnexosNotificacao {

    /** Se este fornecedor responde pelo gatilho informado. */
    boolean atende(String chaveGatilho);

    /**
     * Os anexos ativos do registro. Lista vazia é resposta legítima — a maioria dos
     * registros não tem anexo nenhum, e isso não impede o e-mail de sair.
     */
    List<AnexoNotificacao> anexosDe(UUID idReferencia);
}
