package com.nexo.app.modules.dashboard.application.dto;

import com.nexo.app.shared.application.SelecaoMultipla;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Recorte das notas que compõem um número dos dashboards de execução. Datas são dias
 * civis de São Paulo; sem período, vale o dia de hoje.
 */
public record NotasDoRecorteFilter(
        @Parameter(description = "Primeiro dia do período (yyyy-MM-dd)")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate inicio,

        @Parameter(description = "Último dia do período, inclusive (yyyy-MM-dd)")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate fim,

        @Parameter(description = "Bases operacionais (repetir o parâmetro ou separar por vírgula)")
        List<UUID> idBaseOperacional,
        @Parameter(description = "Processos (repetir o parâmetro ou separar por vírgula)")
        List<UUID> idProcesso,
        UUID idEquipe,

        @Parameter(description = "Tipo de serviço da nota")
        UUID idTipoServico,

        @Parameter(description = "Apenas notas sem tipo de serviço informado")
        Boolean semTipoServico
) {

    public NotasDoRecorteFilter {
        idBaseOperacional = SelecaoMultipla.normalizar(idBaseOperacional);
        idProcesso = SelecaoMultipla.normalizar(idProcesso);
    }
}
