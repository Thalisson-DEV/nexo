package com.nexo.app.modules.financeiro.domain;

/**
 * Meio pelo qual a despesa será liquidada.
 *
 * <p>{@link #BOLETO}, {@link #PIX_TRANSFERENCIA} e {@link #CARTAO_CREDITO} são as
 * formas que costumam exigir dados de pagamento e vencimento — a obrigatoriedade
 * concreta desses campos é decidida pela configuração do formulário, não aqui.</p>
 */
public enum FormaPagamento implements Rotulavel {

    /** À vista, em espécie. */
    A_VISTA_ESPECIE("À vista (espécie)", false),

    /** PIX ou transferência bancária. */
    PIX_TRANSFERENCIA("Pix ou Transferência", true),

    /** Boleto bancário. */
    BOLETO("Boleto", true),

    /** Cartão de crédito corporativo. */
    CARTAO_CREDITO("Cartão de Crédito", true),

    /** Cartão de débito corporativo. */
    CARTAO_DEBITO("Cartão de Débito", false),

    /** Outra forma, a detalhar nas observações. */
    OUTRA("Outra", false);

    private final String rotulo;
    private final boolean exigeDadosPagamento;

    FormaPagamento(String rotulo, boolean exigeDadosPagamento) {
        this.rotulo = rotulo;
        this.exigeDadosPagamento = exigeDadosPagamento;
    }

    /** Texto exibido ao usuário; publicado no schema para a tela não replicá-lo. */
    @Override
    public String getRotulo() {
        return rotulo;
    }

    /**
     * Indica se, por natureza, esta forma de pagamento pressupõe dados bancários /
     * chave PIX para ser liquidada.
     */
    public boolean isExigeDadosPagamento() {
        return exigeDadosPagamento;
    }

    /** Indica se esta forma de pagamento admite parcelamento em mais de uma parcela. */
    public boolean isParcelavel() {
        return this != A_VISTA_ESPECIE && this != CARTAO_DEBITO;
    }
}
