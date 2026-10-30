package com.nexo.app.modules.notificacao;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * API pública do módulo de notificações para outros módulos.
 *
 * <p>O motor de automações publica avisos por aqui — sininho, e-mail opcional,
 * preferências de cada pessoa e idempotência continuam sendo decididos neste módulo —
 * e lê o catálogo de gatilhos para saber a que fatos pode reagir. As regras antigas
 * também saem por aqui, uma vez, para serem convertidas em automações.</p>
 */
public interface NotificacaoApi {

    /** Entrega o aviso; devolve quantas notificações foram de fato gravadas. */
    int publicarAviso(AvisoAutomacao aviso);

    /** Todos os gatilhos que o sistema conhece, com variáveis e papéis. */
    List<GatilhoCatalogado> catalogoGatilhos();

    /** Categorias de aviso (nome → rótulo), na ordem de exibição. */
    Map<String, String> categorias();

    /** Prioridades de aviso (nome → rótulo). */
    Map<String, String> prioridades();

    /** Regras de notificação ainda não convertidas em automação. */
    List<RegraLegada> regrasNaoConvertidas();

    /** Registra a conversão e desliga a regra antiga, para que só a automação avise. */
    void marcarRegraConvertida(UUID idRegra, UUID idAutomacao);
}
