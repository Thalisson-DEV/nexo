package com.nexo.app.modules.rh.application.dto;

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

    List<EquipeMembroRequest> membros
) {}
