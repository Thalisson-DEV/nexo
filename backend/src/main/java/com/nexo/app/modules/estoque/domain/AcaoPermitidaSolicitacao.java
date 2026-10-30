package com.nexo.app.modules.estoque.domain;

/** Acoes que o backend autoriza para uma solicitacao no estado consultado. */
public enum AcaoPermitidaSolicitacao {
    EDITAR_PLANEJAMENTO,
    REGISTRAR_RESERVA,
    ALTERAR_RESERVA,
    APROVAR,
    REJEITAR,
    INICIAR_SEPARACAO,
    CONCLUIR_SEPARACAO,
    PULAR_SEPARACAO,
    ENTREGAR,
    CONFIRMAR_DEVOLUCAO,
    ENCERRAR_SALDO,
    ESTORNAR,
    VER_HISTORICO
}
