package com.nexo.app.modules.frota.application.dto;

import com.nexo.app.modules.frota.domain.CategoriaCnh;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CondutorRequest(
        @NotBlank(message = "Informe o número da CNH.") @Size(max = 20) String cnhNumero,
        @NotNull(message = "Informe a categoria da CNH.") CategoriaCnh cnhCategoria,
        @NotNull(message = "Informe a validade da CNH.") LocalDate cnhValidade,
        LocalDate cnhPrimeiraHabilitacao,
        @Size(max = 1000) String observacao
) {
}
