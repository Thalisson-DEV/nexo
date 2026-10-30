package com.nexo.app.modules.notificacao.domain;

/**
 * Como o gatilho chega ao motor.
 *
 * <p>A distinção não é técnica, é sobre a natureza do fato. Um desconto ser
 * solicitado é um <em>acontecimento</em>: tem instante e emissor. Já "há 10 desvios
 * pendentes" é uma <em>situação</em> — ninguém a causa, ela simplesmente persiste, e
 * amanhã continuará verdadeira sem que evento nenhum seja emitido. Cada uma precisa
 * de um mecanismo diferente.</p>
 */
public enum ModoGatilho {

    /**
     * Um evento de domínio publicado por um módulo. O motor reage na hora, uma vez
     * por ocorrência.
     */
    POR_OCORRENCIA("Por ocorrência"),

    /**
     * Uma contagem lida periodicamente do módulo dono (via
     * {@code ProvedorMetricaGatilho}) e comparada com o limiar da regra.
     */
    POR_LIMIAR("Por limiar");

    private final String rotulo;

    ModoGatilho(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
