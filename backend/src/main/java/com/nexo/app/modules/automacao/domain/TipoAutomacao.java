package com.nexo.app.modules.automacao.domain;

/**
 * Para que serve a automação. Escolhido por quem cria (ou sugerido pelos nós) e
 * conferido na ativação: uma automação de e-mail que não envia e-mail não ativa.
 */
public enum TipoAutomacao {
    EMAIL("E-mail", "Envia e-mails a pessoas de dentro ou de fora do Nexo."),
    ALERTA("Alerta", "Avisa no sininho ou por e-mail quando algo acontece."),
    ROTINA("Rotina", "Roda numa agenda ou a cada evento, como um relatório semanal."),
    APROVACAO("Aprovação", "Pede a decisão de uma pessoa antes de seguir."),
    DADOS_IA("Dados e IA", "Lê, transforma ou interpreta dados, com ou sem IA."),
    GERAL("Geral", "Qualquer outra automação.");

    private final String rotulo;
    private final String descricao;

    TipoAutomacao(String rotulo, String descricao) {
        this.rotulo = rotulo;
        this.descricao = descricao;
    }

    public String getRotulo() {
        return rotulo;
    }

    public String getDescricao() {
        return descricao;
    }
}
