package com.nexo.app.modules.assistente.application.cadastro;

/** Opcao escolhivel: valor gravado, rotulo exibido e um detalhe que ajuda a diferenciar homonimos. */
public record Opcao(String valor, String rotulo, String detalhe) {

    public static Opcao de(String valor, String rotulo) {
        return new Opcao(valor, rotulo, null);
    }
}
