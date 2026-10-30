package com.nexo.app.modules.automacao.application.dto;

import com.nexo.app.modules.automacao.domain.TipoAutomacao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

/** Criação ou edição de automação. A definição é o JSON v3 (gatilho, variáveis, passos). */
public record AutomacaoRequest(
        @NotBlank @Size(max = 120) String nome,
        @Size(max = 500) String descricao,
        TipoAutomacao tipo,
        Map<String, Object> definicao,
        @Size(max = 300) String nota
) {
}
