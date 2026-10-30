package com.nexo.app.modules.obras.application.dto;

import com.nexo.app.modules.obras.domain.TipoObra;
import com.nexo.app.modules.obras.domain.TipoVinculoProjeto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ProjetoRelacionadoResponse(
        UUID idRelacionamento,
        UUID idProjeto,
        String numero,
        String titulo,
        TipoObra tipoObra,
        TipoVinculoProjeto tipoVinculo,
        String notaObra,
        BigDecimal valorProjeto,
        LocalDate dataInicio,
        LocalDate dataPrevisaoFim,
        LocalDate dataFimEfetiva,
        UUID statusId,
        String statusNome,
        boolean ativo
) {
}
