package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.TipoCatalogoSst;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cadastro/edição de uma entrada de catálogo de SST. Na edição o {@code tipo} deve
 * repetir o tipo atual: uma entrada não muda de vocabulário.
 */
public record CatalogoSstRequest(

        @NotNull(message = "O tipo do catálogo é obrigatório")
        @Schema(description = "Vocabulário ao qual a entrada pertence",
                example = "CLASSIFICACAO_EVENTO")
        TipoCatalogoSst tipo,

        @NotBlank(message = "O nome é obrigatório")
        @Size(min = 2, max = 120, message = "O nome deve ter entre 2 e 120 caracteres")
        @Schema(description = "Como a entrada aparece no formulário", example = "Queda de mesmo nível")
        String nome,

        @Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres")
        @Schema(description = "Explicação de quando usar esta entrada")
        String descricao,

        @Size(max = 30, message = "O código deve ter no máximo 30 caracteres")
        @Schema(description = "Código da tabela externa correspondente (NBR 14280, eSocial), quando houver",
                example = "31")
        String codigo,

        @Min(value = 0, message = "A ordem de exibição não pode ser negativa")
        @Schema(description = "Ordem de exibição na lista. Omitido, vale 0 e a lista ordena pelo nome.",
                example = "10")
        Integer ordem
) {
}
