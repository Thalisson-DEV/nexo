package com.nexo.app.modules.financeiro.domain;

/**
 * Assunto a que um campo pertence, e sob o qual ele aparece na tela.
 *
 * <p>É deliberadamente <b>propriedade do campo</b>, e não cadastro: "nº da nota
 * fiscal pertence ao bloco de nota fiscal" é um fato do campo, do mesmo modo que
 * seu tipo e suas opções. Quem configura a variante escolhe quais campos entram —
 * os blocos se formam sozinhos, na ordem declarada aqui, e nunca sobra um grupo
 * vazio nem um campo órfão.</p>
 *
 * <p>A ordem de declaração é a ordem de exibição: é ela que faz o formulário ser
 * lido de cima para baixo na sequência em que a pessoa raciocina — de onde vem a
 * despesa, quem pede, para qual veículo, de quem se compra, quanto custa, se tem
 * nota, e o que mais precisa ser dito.</p>
 */
public enum GrupoCampo implements Rotulavel {

    ORIGEM("Origem do pagamento"),
    IDENTIFICACAO("Identificação"),
    FROTA("Veículo e serviço"),
    FORNECEDOR("Fornecedor"),
    VALORES("Valores e condições"),
    NOTA_FISCAL("Nota fiscal"),
    COMPLEMENTO("Descrição e observações");

    private final String rotulo;

    GrupoCampo(String rotulo) {
        this.rotulo = rotulo;
    }

    /** Título exibido acima do bloco, na tela e no schema publicado. */
    @Override
    public String getRotulo() {
        return rotulo;
    }

    /** Posição do bloco no formulário; deriva da ordem de declaração. */
    public int getOrdem() {
        return ordinal();
    }
}
