package com.nexo.app.modules.obras.domain;

/**
 * Ciclo do item planejado. Só o item pendente pode ser alterado ou removido no planejamento.
 */
public enum StatusItemPlanejado {
    PENDENTE,
    PROGRAMADO,
    EM_ANDAMENTO,
    CONCLUIDO,
    MEDIDO
}
