package com.nexo.app.modules.automacao.domain.expressao;

/** Expressão mal escrita ou que não pôde ser avaliada. A mensagem vai para a tela. */
public class ErroExpressao extends RuntimeException {

    public ErroExpressao(String mensagem) {
        super(mensagem);
    }
}
