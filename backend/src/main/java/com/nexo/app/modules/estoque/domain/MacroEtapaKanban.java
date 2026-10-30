package com.nexo.app.modules.estoque.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Macro-etapas operacionais para o quadro Kanban de solicitações técnicas.
 * Agrupa os sub-status operacionais em 4 visões principais.
 */
@Schema(description = "Macro-etapa do Kanban operacional de solicitações técnicas")
@Getter
@RequiredArgsConstructor
public enum MacroEtapaKanban {
    PLANEJAMENTO("Planejamento", "pi pi-calendar"),
    SEPARACAO("Separação", "pi pi-box"),
    ENTREGA("Entrega & Campo", "pi pi-truck"),
    RECUSADO("Recusado", "pi pi-times-circle");

    private final String label;
    private final String icon;
}
