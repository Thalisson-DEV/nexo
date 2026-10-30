package com.nexo.app.modules.rh.application.dto;

import com.nexo.app.modules.rh.PeriodicidadeMetaFaturamento;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Edição de uma meta de faturamento. A equipe não muda: para outra equipe, cadastre outra meta. */
public record MetaFaturamentoUpdateRequest(

        @NotNull(message = "O valor da meta é obrigatório")
        @Positive(message = "O valor da meta deve ser maior que zero")
        @Digits(integer = 10, fraction = 2, message = "O valor da meta deve ter no máximo 10 dígitos inteiros e 2 decimais")
        BigDecimal valor,

        @NotNull(message = "A periodicidade é obrigatória")
        PeriodicidadeMetaFaturamento periodicidade,

        @NotNull(message = "O início da vigência é obrigatório")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate vigenciaInicio,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate vigenciaFim,

        @Size(max = 200, message = "A descrição deve ter no máximo 200 caracteres")
        String descricao
) {
}
