package com.nexo.app.modules.financeiro.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

/**
 * Envio de uma solicitação, no formato genérico que sustenta os formulários
 * moldáveis: um mapa de chave do campo para o valor preenchido.
 *
 * <p>O servidor valida cada chave contra o schema da variante escolhida — chaves
 * desconhecidas são recusadas, campos ocultos pela condição são ignorados e
 * obrigatórios ausentes são reportados de uma vez. Nada aqui é aceito por
 * confiança no cliente: o usuário solicitante vem do token, nunca do payload.</p>
 */
public record SolicitacaoRequest(

        @Schema(description = "Variante de formulário utilizada")
        @NotNull(message = "O formulário é obrigatório.")
        UUID formularioId,

        @Schema(description = "Valores preenchidos, indexados pela chave do campo",
                example = """
                        {
                          "solicitanteColaboradorId": "3f1a...",
                          "baseId": "9c2b...",
                          "processoId": "77aa...",
                          "valor": 1500.00,
                          "temNf": true,
                          "naturezaServico": "SUSPENSAO_FREIOS"
                        }""")
        Map<String, Object> valores
) {

    /** Nunca nulo, para simplificar o consumo na validação. */
    public Map<String, Object> valoresOuVazio() {
        return valores == null ? Map.of() : valores;
    }
}
