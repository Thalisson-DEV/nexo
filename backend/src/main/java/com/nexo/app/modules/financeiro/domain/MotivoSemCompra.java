package com.nexo.app.modules.financeiro.domain;

/**
 * Justificativa para um pagamento que não nasceu de uma solicitação de compra.
 *
 * <p>Só é preenchida quando {@code possuiCompraPrevia} é falso — a condição está
 * declarada em {@link CampoCanonico}, não no cadastro do formulário.</p>
 */
public enum MotivoSemCompra implements Rotulavel {

    COMBUSTIVEL_FORNECEDOR_CADASTRADO("Combustíveis em fornecedor cadastrado"),

    DESPESA_MENSAL_RECORRENTE("Despesa mensal recorrente (água, energia, plano de saúde, sistemas, "
            + "locação, telefonia, internet, serviços contratados, seguros)"),

    OUTRA("Outra");

    private final String rotulo;

    MotivoSemCompra(String rotulo) {
        this.rotulo = rotulo;
    }

    @Override
    public String getRotulo() {
        return rotulo;
    }
}
