package com.nexo.app.modules.dashboard.application.dto;

import com.nexo.app.shared.application.SelecaoMultipla;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Recorte do dashboard operacional. Datas são dias civis de São Paulo, inclusivos;
 * sem datas, o recorte é o mês corrente até hoje.
 */
public record DashboardOperacionalFilter(
        @Parameter(description = "Bases operacionais (repetir o parâmetro ou separar por vírgula)")
        List<UUID> idBaseOperacional,
        @Parameter(description = "Processos (repetir o parâmetro ou separar por vírgula)")
        List<UUID> idProcesso,

        @Parameter(description = "Primeiro dia do período (yyyy-MM-dd). Padrão: dia 1 do mês corrente.")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate inicio,

        @Parameter(description = "Último dia do período, inclusivo (yyyy-MM-dd). Padrão: hoje.")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate fim
) {

    public DashboardOperacionalFilter {
        idBaseOperacional = SelecaoMultipla.normalizar(idBaseOperacional);
        idProcesso = SelecaoMultipla.normalizar(idProcesso);
    }
}
