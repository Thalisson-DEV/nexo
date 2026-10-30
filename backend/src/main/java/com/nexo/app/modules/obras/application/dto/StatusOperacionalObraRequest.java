package com.nexo.app.modules.obras.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record StatusOperacionalObraRequest(
        @NotNull(message = "O status é obrigatório")
        UUID idStatusObra,

        @Size(max = 1000, message = "A observação deve ter até 1000 caracteres")
        String observacao,

        @JsonFormat(pattern = "dd/MM/yyyy")
        LocalDate dataFimEfetiva
) {
}
