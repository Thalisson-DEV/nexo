package com.nexo.app.modules.financeiro.domain;

/**
 * Ciclo de vida de uma solicitação (compra ou pagamento).
 *
 * <pre>
 *   RASCUNHO ──submeter──▶ SOLICITADO ──aprovar──▶ APROVADO ─┐
 *      │                    ▲  │                             │
 *      │                    │  ├──rejeitar──▶ REJEITADO ─────┤
 *      │                    └──┼──────desfazer decisão───────┘
 *      └───────cancelar────────┴──cancelar──▶ CANCELADO
 * </pre>
 *
 * <p>Aprovado e rejeitado não admitem edição nem cancelamento, mas não são
 * irreversíveis: quem tem alçada pode desfazer a decisão, com motivo, e a
 * solicitação volta a aguardar aprovação.</p>
 *
 * <p>O rascunho existe para que o solicitante possa anexar arquivos e preencher o
 * formulário em etapas sem poluir a fila dos diretores. A validação completa contra
 * o schema do formulário só é exigida na submissão.</p>
 */
public enum StatusSolicitacao {

    /** Em preenchimento pelo solicitante; não aparece para aprovação. */
    RASCUNHO(true, false),

    /** Submetida e aguardando decisão de quem tem alçada. */
    SOLICITADO(true, false),

    /** Autorizada. Passa a valer o {@code valorAprovado}. */
    APROVADO(false, true),

    /** Negada, com justificativa obrigatória. */
    REJEITADO(false, true),

    /** Desistência do solicitante. */
    CANCELADO(false, true);

    private final boolean editavel;
    private final boolean finalizado;

    StatusSolicitacao(boolean editavel, boolean finalizado) {
        this.editavel = editavel;
        this.finalizado = finalizado;
    }

    /** Indica se o conteúdo do formulário e os anexos ainda podem ser alterados. */
    public boolean isEditavel() {
        return editavel;
    }

    /** Indica se a solicitação já teve desfecho e não admite novas transições. */
    public boolean isFinalizado() {
        return finalizado;
    }
}
