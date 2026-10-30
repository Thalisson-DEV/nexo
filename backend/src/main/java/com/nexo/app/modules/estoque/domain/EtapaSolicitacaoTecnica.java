package com.nexo.app.modules.estoque.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** Etapas operacionais aceitas como filtro da fila tecnica. */
@Schema(description = "Etapa operacional usada para filtrar filas tecnicas")
public enum EtapaSolicitacaoTecnica {
    AGUARDANDO_PLANEJAMENTO,
    EM_SEPARACAO,
    PENDENTE_ENTREGA
}
