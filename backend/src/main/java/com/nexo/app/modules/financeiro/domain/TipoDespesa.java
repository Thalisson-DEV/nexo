package com.nexo.app.modules.financeiro.domain;

/**
 * Classificação da despesa de um pagamento, usada para agrupar o gasto por
 * natureza nos relatórios.
 */
public enum TipoDespesa implements Rotulavel {

    MATERIAL("Material"),
    SERVICO("Serviço"),
    COMBUSTIVEL("Combustível"),
    LOCACAO("Locação"),
    UTILIDADES("Utilidades (água, energia, telefonia, internet)"),
    TRIBUTOS("Tributos e taxas"),
    MANUTENCAO("Manutenção"),
    OUTRA("Outra");

    private final String rotulo;

    TipoDespesa(String rotulo) {
        this.rotulo = rotulo;
    }

    @Override
    public String getRotulo() {
        return rotulo;
    }
}
