package com.nexo.app.modules.obras.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nexo.app.modules.obras.domain.TipoObra;
import com.nexo.app.modules.obras.domain.TipoVinculoSubprojeto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ObraResumoResponse(
        UUID id,
        String numero,
        String titulo,
        TipoObra tipoObra,
        String notaObra,
        BigDecimal valorProjeto,
        @JsonFormat(pattern = "dd/MM/yyyy") LocalDate dataInicio,
        @JsonFormat(pattern = "dd/MM/yyyy") LocalDate dataPrevisaoFim,
        @JsonFormat(pattern = "dd/MM/yyyy") LocalDate dataFimEfetiva,
        UUID statusId,
        String statusNome,
        boolean statusConcluida,
        UUID processoId,
        String processoNome,
        UUID baseOperacionalId,
        String baseOperacionalNome,
        UUID municipioId,
        String municipioNome,
        UUID centroCustoId,
        String centroCustoNome,
        UUID supervisorId,
        String supervisorNome,
        UUID coordenadorId,
        String coordenadorNome,
        UUID agenteResponsavelId,
        String agenteResponsavelNome,
        @Deprecated(forRemoval = true) UUID obraPaiId,
        @Deprecated(forRemoval = true) String obraPaiNumero,
        @Deprecated(forRemoval = true) TipoVinculoSubprojeto tipoVinculoPai,
        int totalEquipes,
        int totalProjetosRelacionados,
        @Deprecated(forRemoval = true) int totalSubprojetos,
        int totalAnexos,
        boolean ativo
) {
}
