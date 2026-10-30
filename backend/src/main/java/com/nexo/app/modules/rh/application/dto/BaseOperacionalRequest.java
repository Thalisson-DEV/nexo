package com.nexo.app.modules.rh.application.dto;

import com.nexo.app.shared.domain.UnidadeFederativa;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record BaseOperacionalRequest(
    @NotBlank(message = "Nome da base é obrigatório")
    String nome,

    @NotBlank(message = "UF da base é obrigatória")
    @Pattern(regexp = UnidadeFederativa.REGEX, message = "Informe uma UF brasileira válida")
    String uf,

    @NotBlank(message = "Endereço da base é obrigatório")
    @Size(max = 300, message = "O endereço da base deve ter no máximo 300 caracteres")
    String endereco,

    @NotNull(message = "ID do Centro de Custo é obrigatório")
    UUID centroCustoId,

    Double latitude,
    Double longitude,
    Integer raio
) {}
