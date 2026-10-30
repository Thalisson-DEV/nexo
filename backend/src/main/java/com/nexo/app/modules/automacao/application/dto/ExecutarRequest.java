package com.nexo.app.modules.automacao.application.dto;

import java.util.Map;

/** Execução manual ou de teste, com as informações pedidas pelo gatilho manual. */
public record ExecutarRequest(
        Map<String, Object> inputs,
        boolean teste
) {
}
