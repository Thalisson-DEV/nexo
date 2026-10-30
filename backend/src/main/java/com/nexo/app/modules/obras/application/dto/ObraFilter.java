package com.nexo.app.modules.obras.application.dto;

import com.nexo.app.modules.obras.domain.TipoObra;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ObraFilter(
        String buscaGlobal,
        String numero,
        String titulo,
        String notaObra,
        TipoObra tipoObra,
        UUID statusId,
        List<UUID> statusIds,
        UUID processoId,
        List<UUID> processoIds,
        UUID baseOperacionalId,
        List<UUID> baseOperacionalIds,
        UUID municipioId,
        List<UUID> municipioIds,
        UUID centroCustoId,
        UUID supervisorId,
        List<UUID> supervisorIds,
        UUID coordenadorId,
        List<UUID> coordenadorIds,
        UUID agenteResponsavelId,
        UUID equipeId,
        UUID projetoRelacionadoId,
        @Deprecated(forRemoval = true) Boolean apenasPrincipais,
        @Deprecated(forRemoval = true) UUID obraPaiId,
        @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataInicioDe,
        @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataInicioAte,
        @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataPrevisaoFimDe,
        @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataPrevisaoFimAte,
        BigDecimal valorProjetoMin,
        BigDecimal valorProjetoMax,
        Boolean ativo
) {
}
