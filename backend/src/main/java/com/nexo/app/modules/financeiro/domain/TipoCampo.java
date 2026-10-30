package com.nexo.app.modules.financeiro.domain;

/**
 * Natureza do dado coletado por um campo. É o contrato que o frontend usa para
 * escolher o componente de tela e o backend usa para validar e converter o valor
 * recebido — nenhum dos dois lados adivinha.
 *
 * <p>Cada {@link CampoCanonico} declara o seu; não é escolha de quem configura o
 * formulário.</p>
 */
public enum TipoCampo {

    /** Linha única de texto. */
    TEXTO,

    /** Área de texto multilinha (descrições, observações, rateio). */
    TEXTO_LONGO,

    /** Número inteiro (ex.: quantidade de parcelas). */
    INTEIRO,

    /** Número com casas decimais (ex.: quantidade fracionada). */
    DECIMAL,

    /** Valor monetário em reais, com duas casas decimais. */
    MOEDA,

    /** Data sem hora. */
    DATA,

    /**
     * Sim / Não. A tela deve pedir a resposta explicitamente, e não por uma caixa
     * de marcação: campos condicionados a um booleano precisam distinguir "não" de
     * "ainda não respondi".
     */
    BOOLEANO,

    /** Escolha de exatamente uma opção da enumeração que apoia o campo. */
    SELECAO_UNICA,

    /**
     * Ponteiro para um registro de outro cadastro (base, fornecedor, veículo...).
     * O valor trafega como UUID e a origem das opções é a {@link FonteReferencia}.
     */
    REFERENCIA;

    /** Indica se o campo escolhe entre valores fechados. */
    public boolean isEscolha() {
        return this == SELECAO_UNICA;
    }

    /** Indica se o valor é numérico e, portanto, comparável contra mínimo/máximo. */
    public boolean isNumerico() {
        return this == INTEIRO || this == DECIMAL || this == MOEDA;
    }
}
