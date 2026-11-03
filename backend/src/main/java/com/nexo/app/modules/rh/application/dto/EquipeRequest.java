package com.nexo.app.modules.rh.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record EquipeRequest(
    @NotBlank(message = "Código da equipe é obrigatório")
    String codigo,

    @NotNull(message = "ID do tipo de equipe é obrigatório")
    UUID idTipoEquipe,

    @NotNull(message = "ID da base operacional é obrigatória")
    UUID idBaseOperacional,

    @NotNull(message = "ID do veículo é obrigatório")
    UUID idVeiculo,

    @NotNull(message = "ID do coordenador é obrigatório")
    UUID idCoordenador,

    @NotNull(message = "ID do supervisor é obrigatório")
    UUID idSupervisor,

    @NotNull(message = "ID do processo é obrigatório")
    UUID idProcesso,

    UUID idContrato,

    List<EquipeMembroRequest> membros,

    @Min(value = 1, message = "A jornada deve ser maior que zero")
    @Max(value = 1440, message = "A jornada deve ter até 24 horas")
    Integer jornadaMinutos,

    @Min(value = 0, message = "O tempo de plataforma não pode ser negativo")
    Integer plataformaMinutos
) {
    public EquipeRequest(String codigo, UUID idTipoEquipe, UUID idBaseOperacional, UUID idVeiculo, UUID idCoordenador,
                         UUID idSupervisor, UUID idProcesso, UUID idContrato, List<EquipeMembroRequest> membros) {
        this(codigo, idTipoEquipe, idBaseOperacional, idVeiculo, idCoordenador, idSupervisor, idProcesso, idContrato, membros, null, null);
    }
}
