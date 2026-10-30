package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.OrigemMembroCipa;
import com.nexo.app.modules.sst.domain.PapelMembroGovernancaSst;
import com.nexo.app.modules.sst.domain.RepresentacaoCipa;
import com.nexo.app.modules.sst.domain.TitularidadeCipa;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record MembroCorpoSstRequest(
        @NotNull UUID idColaborador,
        @NotNull PapelMembroGovernancaSst papel,
        RepresentacaoCipa representacao,
        TitularidadeCipa titularidade,
        OrigemMembroCipa origem,
        @Size(max = 120) String cicloEleitoral,
        @Min(1) Integer classificacaoEleitoral,
        @Size(max = 180) String atoDesignacao,
        boolean reeleito,
        @NotNull LocalDate inicioParticipacao,
        @Size(max = 80) String turnoDisponivel
) {
}
