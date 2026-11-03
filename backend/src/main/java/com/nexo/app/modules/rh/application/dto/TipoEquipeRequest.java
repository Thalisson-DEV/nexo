package com.nexo.app.modules.rh.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TipoEquipeRequest(
    @NotBlank(message = "O nome é obrigatório")
    @Size(max = 50, message = "O nome deve ter no máximo 50 caracteres")
    String nome,

    @Min(value = 1, message = "A jornada deve ser maior que zero")
    @Max(value = 1440, message = "A jornada deve ter até 24 horas")
    Integer jornadaMinutos,

    @Min(value = 0, message = "O tempo de plataforma não pode ser negativo")
    Integer plataformaMinutos
) {}
