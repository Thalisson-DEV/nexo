package com.nexo.app.modules.rh.application.dto;

import com.nexo.app.modules.rh.PeriodicidadeMetaFaturamento;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Cadastro de metas de faturamento. Uma meta é criada para cada equipe informada,
 * todas com o mesmo valor e a mesma vigência — tudo ou nada.
 */
public record MetaFaturamentoRequest(

        @NotEmpty(message = "Informe ao menos uma equipe")
        @Size(max = 200, message = "Cadastre no máximo 200 equipes por vez")
        @Schema(description = "Equipes que recebem a meta (uma meta por equipe)")
        List<@NotNull UUID> idsEquipes,

        @NotNull(message = "O valor da meta é obrigatório")
        @Positive(message = "O valor da meta deve ser maior que zero")
        @Digits(integer = 10, fraction = 2, message = "O valor da meta deve ter no máximo 10 dígitos inteiros e 2 decimais")
        @Schema(description = "Faturamento esperado por janela (ciclo ou mês), em reais", example = "32000.00")
        BigDecimal valor,

        @NotNull(message = "A periodicidade é obrigatória")
        @Schema(description = "CICLO_COMERCIAL (26 a 25) ou MENSAL (mês civil)")
        PeriodicidadeMetaFaturamento periodicidade,

        @NotNull(message = "O início da vigência é obrigatório")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Schema(description = "Primeiro dia da primeira janela (dia 26 no ciclo, dia 1º no mensal)", example = "2026-10-26")
        LocalDate vigenciaInicio,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Schema(description = "Último dia da última janela. Nulo = sem prazo.")
        LocalDate vigenciaFim,

        @Size(max = 200, message = "A descrição deve ter no máximo 200 caracteres")
        String descricao,

        @Schema(description = "Encerra, no fim da janela anterior, a meta sem prazo que a equipe já tem. "
                + "É o jeito de reajustar a meta a partir de um ciclo.")
        boolean substituirVigentes
) {
}
