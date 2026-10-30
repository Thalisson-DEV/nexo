package com.nexo.app.modules.notificacao.application.dto;

import java.time.OffsetDateTime;

/**
 * "Ver depois". Sem data informada, o servidor adia por 24 horas — evita obrigar o
 * front a inventar a política de adiamento.
 */
public record AdiarComunicadoRequest(OffsetDateTime adiarAte) {
}
