package com.nexo.app.modules.sst.domain;

/**
 * Vocabulários que o próprio usuário mantém para classificar um evento de SST.
 *
 * <p>Os quatro tipos têm exatamente a mesma forma — nome, descrição, código externo
 * e ordem de exibição —, e por isso dividem uma única tabela discriminada por este
 * enum em vez de quatro cadastros idênticos. A consequência prática é que introduzir
 * um novo vocabulário (as causas da investigação, por exemplo) custa uma constante
 * aqui, e não uma migration.</p>
 *
 * <p>O preço dessa escolha é que o banco não consegue garantir, por chave estrangeira,
 * que a coluna de classificação de um evento aponte para uma classificação e não para
 * um local de lesão. Essa garantia é feita na escrita, pelo use case.</p>
 */
public enum TipoCatalogoSst {

    /** Como a empresa classifica o evento (ex.: "Queda de mesmo nível", "Choque elétrico"). */
    CLASSIFICACAO_EVENTO("Classificação do evento"),

    /** Parte do corpo atingida (ex.: "Mão direita", "Joelho esquerdo"). */
    LOCAL_LESAO("Local da lesão"),

    /** Natureza da lesão sofrida (ex.: "Corte", "Fratura", "Queimadura"). */
    NATUREZA_LESAO("Natureza da lesão"),

    /** O que causou a lesão ou o dano (ex.: "Ferramenta manual", "Produto químico"). */
    AGENTE_CAUSADOR("Agente causador");

    private final String rotulo;

    TipoCatalogoSst(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
