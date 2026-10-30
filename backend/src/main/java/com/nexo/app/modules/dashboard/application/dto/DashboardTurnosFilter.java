package com.nexo.app.modules.dashboard.application.dto;

import com.nexo.app.shared.application.SelecaoMultipla;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Recorte do dashboard de turnos: um dia civil de São Paulo (padrão: hoje). */
public record DashboardTurnosFilter(
        @Parameter(description = "Bases operacionais (repetir o parâmetro ou separar por vírgula)")
        List<UUID> idBaseOperacional,
        @Parameter(description = "Processos (repetir o parâmetro ou separar por vírgula)")
        List<UUID> idProcesso,

        @Parameter(description = "Dia analisado (yyyy-MM-dd). Padrão: hoje.")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate data
) {

    public DashboardTurnosFilter {
        idBaseOperacional = SelecaoMultipla.normalizar(idBaseOperacional);
        idProcesso = SelecaoMultipla.normalizar(idProcesso);
    }
}
