package com.nexo.app.modules.obras.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nexo.app.modules.obras.domain.TipoObra;
import com.nexo.app.modules.obras.domain.TipoVinculoSubprojeto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record ObraRequest(
        @NotBlank(message = "O número da obra é obrigatório")
        @Size(min = 1, max = 7, message = "O número da obra deve ter até 7 caracteres")
        String numero,

        @NotBlank(message = "O título da obra é obrigatório")
        @Size(min = 2, max = 300, message = "O título da obra deve ter entre 2 e 300 caracteres")
        String titulo,

        @Size(max = 1000, message = "A descrição deve ter no máximo 1000 caracteres")
        String descricao,

        @Size(max = 50, message = "A nota da obra deve ter no máximo 50 caracteres")
        String notaObra,

        BigDecimal valorProjeto,

        @NotNull(message = "A data de início da obra é obrigatória")
        @JsonFormat(pattern = "dd/MM/yyyy")
        LocalDate dataInicio,

        @NotNull(message = "A data prevista de conclusão é obrigatória")
        @JsonFormat(pattern = "dd/MM/yyyy")
        LocalDate dataPrevisaoFim,

        @JsonFormat(pattern = "dd/MM/yyyy")
        LocalDate dataFimEfetiva,

        Double latitude,

        Double longitude,

        @NotNull(message = "O processo de negócio da obra é obrigatório")
        UUID idProcesso,

        @NotNull(message = "A base operacional da obra é obrigatória")
        UUID idBaseOperacional,

        @NotNull(message = "O município da obra é obrigatório")
        UUID idMunicipio,

        @NotNull(message = "O centro de custo da obra é obrigatório")
        UUID idCentroCusto,

        @NotNull(message = "O supervisor da obra é obrigatório")
        UUID idSupervisor,

        @NotNull(message = "O coordenador da obra é obrigatório")
        UUID idCoordenador,

        @NotNull(message = "O status da obra é obrigatório")
        UUID idStatusObra,

        UUID idAgenteResponsavel,

        @Deprecated(forRemoval = true) UUID idObraPai,

        @Deprecated(forRemoval = true) TipoVinculoSubprojeto tipoVinculoPai,

        Set<UUID> equipes,

        TipoObra tipoObra
) {
}
