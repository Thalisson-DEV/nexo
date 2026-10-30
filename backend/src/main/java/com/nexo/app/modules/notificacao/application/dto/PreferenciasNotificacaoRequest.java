package com.nexo.app.modules.notificacao.application.dto;

import java.util.List;

import com.nexo.app.modules.notificacao.domain.CategoriaNotificacao;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Atualização em lote das preferências. O envio é parcial: só as categorias
 * presentes são alteradas, as demais permanecem como estavam.
 */
public record PreferenciasNotificacaoRequest(
        @NotEmpty(message = "Informe ao menos uma preferência.")
        @Valid
        List<ItemPreferencia> preferencias
) {

    public record ItemPreferencia(
            @NotNull(message = "A categoria é obrigatória.")
            CategoriaNotificacao categoria,

            @NotNull(message = "Informe se a categoria deve aparecer na aplicação.")
            Boolean inApp,

            @NotNull(message = "Informe se a categoria deve ser enviada por e-mail.")
            Boolean email
    ) {
    }
}
