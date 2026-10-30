package com.nexo.app.modules.obras.application.dto;

import com.nexo.app.modules.obras.domain.TipoVinculoSubprojeto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Deprecated(forRemoval = true)
public record SubprojetoResponse(
        UUID id,
        String numero,
        String titulo,
        String notaObra,
        BigDecimal valorProjeto,
        LocalDate dataInicio,
        LocalDate dataPrevisaoFim,
        LocalDate dataFimEfetiva,
        TipoVinculoSubprojeto tipoVinculo,
        UUID statusId,
        String statusNome
) {
}
