package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.PeriodicidadeMeta;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Cadastro/edição de uma meta de inspeções. Informe {@code idFuncao} <b>ou</b>
 * {@code idColaborador} — nunca os dois. Na edição, o alvo não pode ser trocado.
 */
public record MetaInspecaoRequest(

        @NotNull(message = "O tipo de inspeção é obrigatório")
        @Schema(description = "Tipo de inspeção alvo da meta")
        UUID idTipoInspecao,

        @Schema(description = "Função alvo da meta. Exclusivo com idColaborador.")
        UUID idFuncao,

        @Schema(description = "Colaborador alvo da meta (exceção individual). Exclusivo com idFuncao.")
        UUID idColaborador,

        @NotNull(message = "A quantidade de inspeções é obrigatória")
        @Min(value = 1, message = "A quantidade de inspeções deve ser maior que zero")
        @Max(value = 999, message = "A quantidade de inspeções não pode ultrapassar 999")
        @Schema(description = "Inspeções exigidas por janela de apuração", example = "4")
        Integer quantidade,

        @NotNull(message = "A periodicidade é obrigatória")
        @Schema(description = "Define a duração de cada janela, que se repete a partir do início da vigência")
        PeriodicidadeMeta periodicidade,

        @NotNull(message = "A data de início da vigência é obrigatória")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Schema(description = "Âncora da meta: primeira janela começa nesta data", example = "2026-08-01")
        LocalDate vigenciaInicio,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Schema(description = "Fim da vigência. Nulo = meta por prazo indeterminado. Obrigatório em PERSONALIZADO.")
        LocalDate vigenciaFim,

        @Size(max = 200, message = "A descrição deve ter no máximo 200 caracteres")
        @Schema(description = "Identificação da meta na listagem", example = "Meta mensal de inspeções — encarregados")
        String descricao
) {
}
