package com.nexo.app.shared.application.notificacao;

import java.util.List;

/**
 * Publica, para o motor de notificações, contagens de estado pertencentes a um
 * módulo do Nexo.
 *
 * <p>Existe porque "há 10 desvios pendentes" não é um acontecimento: é uma
 * situação. Nenhum evento é emitido quando o décimo desvio continua em aberto no dia
 * seguinte, então esse tipo de regra precisa ser avaliado por varredura periódica —
 * e quem sabe contar desvios é o SST, não o módulo de notificação.</p>
 *
 * <p>O administrador configura o limiar, o texto e os destinatários; a consulta em
 * si nunca é configurável. É o que impede que a moldabilidade da regra vire uma
 * porta para SQL arbitrário.</p>
 */
public interface ProvedorMetricaGatilho {

    /** Gatilhos que este módulo sabe medir. */
    List<String> chavesGatilho();

    /** Valor atual da métrica do gatilho informado. */
    long medir(String chaveGatilho);
}
