package com.nexo.app.modules.estoque.domain;

import java.util.Objects;

public record AlteracaoEventoSolicitacao(
        String caminho,
        Object valorAnterior,
        Object valorNovo
) {
    public AlteracaoEventoSolicitacao {
        Objects.requireNonNull(caminho, "O caminho da alteracao e obrigatorio");
    }

    /**
     * Nome de contrato usado pela tela para o caminho do campo alterado.
     *
     * <p>{@link #caminho()} permanece como a representação canônica para não
     * quebrar eventos já persistidos.</p>
     */
    public String campo() {
        return caminho;
    }

    /** Nome de contrato usado pela tela para o valor depois do comando. */
    public Object valorAtual() {
        return valorNovo;
    }
}
