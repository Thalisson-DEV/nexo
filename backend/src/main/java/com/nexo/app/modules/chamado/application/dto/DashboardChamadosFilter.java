package com.nexo.app.modules.chamado.application.dto;

import com.nexo.app.modules.chamado.domain.GranularidadeDashboard;
import com.nexo.app.modules.chamado.domain.PrioridadeChamado;
import com.nexo.app.modules.chamado.domain.StatusChamado;
import com.nexo.app.shared.application.SelecaoMultipla;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Filtro dos dashboards gerenciais de Chamados. Quando inicio/fim sao omitidos,
 * o periodo padrao e o mes corrente.
 * Todos os recortes aceitam multipla selecao.
 */
public record DashboardChamadosFilter(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime inicio,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime fim,

        List<UUID> topicoId,
        List<UUID> equipeId,
        List<PrioridadeChamado> prioridade,
        List<StatusChamado> status,
        List<UUID> usuarioResponsavelId,
        List<UUID> usuarioSolicitanteId,

        @Parameter(description = "Se informado como true, restringe a chamados com qualquer SLA vencido")
        Boolean slaVencido,

        @Parameter(description = "Recorte das series do dashboard (DIA, MES ou ANO). Omitido, e escolhido pela duracao do periodo.")
        GranularidadeDashboard granularidade
) {

    public DashboardChamadosFilter {
        topicoId = SelecaoMultipla.normalizar(topicoId);
        equipeId = SelecaoMultipla.normalizar(equipeId);
        prioridade = SelecaoMultipla.normalizar(prioridade);
        status = SelecaoMultipla.normalizar(status);
        usuarioResponsavelId = SelecaoMultipla.normalizar(usuarioResponsavelId);
        usuarioSolicitanteId = SelecaoMultipla.normalizar(usuarioSolicitanteId);
    }

    public static DashboardChamadosFilter vazio() {
        return new DashboardChamadosFilter(null, null, null, null, null, null, null, null, null, null);
    }
}
