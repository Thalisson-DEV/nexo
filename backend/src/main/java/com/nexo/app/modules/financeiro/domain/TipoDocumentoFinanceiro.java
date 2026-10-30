package com.nexo.app.modules.financeiro.domain;

/**
 * Natureza do documento guardado no repositório financeiro.
 *
 * <p>Classificar é o que separa um repositório de uma pasta de arquivos: é por
 * este campo que a contabilidade encontra a nota fiscal de um pagamento sem abrir
 * cada anexo, e é ele que decide quais documentos precisam de identificação
 * fiscal para valerem como comprovação.</p>
 */
public enum TipoDocumentoFinanceiro implements Rotulavel {

    /** Nota fiscal de produto ou serviço. */
    NOTA_FISCAL("Nota fiscal", true, true),

    /** Boleto bancário a ser pago. */
    BOLETO("Boleto", true, false),

    /** Recibo simples, sem valor fiscal pleno. */
    RECIBO("Recibo", true, false),

    /** Comprovante de que o pagamento foi efetuado (transferência, PIX, quitação). */
    COMPROVANTE_PAGAMENTO("Comprovante de pagamento", false, false),

    /** Orçamento do fornecedor, anterior à decisão. */
    ORCAMENTO("Orçamento", false, false),

    /** Contrato, aditivo ou proposta assinada. */
    CONTRATO("Contrato", false, false),

    /** Extrato ou demonstrativo bancário. */
    EXTRATO("Extrato bancário", false, false),

    /** Foto da situação que motivou a solicitação. */
    FOTO("Foto", false, false),

    /** Qualquer outro documento de apoio. */
    OUTRO("Outro", false, false);

    private final String rotulo;
    private final boolean exigeIdentificacao;
    private final boolean fiscal;

    TipoDocumentoFinanceiro(String rotulo, boolean exigeIdentificacao, boolean fiscal) {
        this.rotulo = rotulo;
        this.exigeIdentificacao = exigeIdentificacao;
        this.fiscal = fiscal;
    }

    @Override
    public String getRotulo() {
        return rotulo;
    }

    /**
     * Indica se o documento perde utilidade sem número identificador.
     *
     * <p>Não é invariante de criação de propósito: recusar o arquivo por falta do
     * número faria a pessoa desistir de anexar, e um documento sem número no
     * repositório vale mais do que um documento fora dele. A ausência vira métrica
     * de qualidade no dashboard, onde alguém pode cobrá-la.</p>
     */
    public boolean exigeIdentificacao() {
        return exigeIdentificacao;
    }

    /**
     * Documento que serve como comprovação fiscal da despesa. É o que sustenta o
     * indicador de conformidade: uma solicitação aprovada sem nenhum documento
     * fiscal é uma despesa sem lastro na contabilidade.
     */
    public boolean isFiscal() {
        return fiscal;
    }
}
