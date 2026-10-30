package com.nexo.app.modules.financeiro.domain;

/**
 * Domínios de solicitação atendidos pelo módulo financeiro. Cada tipo possui seus
 * próprios formulários (variantes) e sua própria tabela de alçadas de aprovação.
 */
public enum TipoSolicitacao {

    /** Pedido de autorização para adquirir material ou serviço. */
    COMPRA,

    /** Pedido de autorização para efetuar um pagamento (com ou sem compra prévia). */
    PAGAMENTO
}
