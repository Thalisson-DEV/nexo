package com.nexo.app.modules.dashboard.application.usecases;

import com.nexo.app.modules.dashboard.application.dto.ConsultaTurnosFilter;
import com.nexo.app.modules.dashboard.application.dto.ContextoExecucaoResponse;
import com.nexo.app.modules.dashboard.application.dto.DashboardOperacionalFilter;
import com.nexo.app.modules.dashboard.application.dto.DashboardOperacionalResponse;
import com.nexo.app.modules.dashboard.application.dto.DashboardTurnosFilter;
import com.nexo.app.modules.dashboard.application.dto.DashboardTurnosResponse;
import com.nexo.app.modules.dashboard.application.dto.DetalhamentoTurnoResponse;
import com.nexo.app.modules.dashboard.application.dto.NotaDoRecorteResponse;
import com.nexo.app.modules.dashboard.application.dto.NotasDoRecorteFilter;
import com.nexo.app.modules.dashboard.application.dto.TurnoConsultaResponse;
import com.nexo.app.shared.application.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Porta de entrada dos dashboards e relatórios de execução. */
@Service
@RequiredArgsConstructor
public class DashboardUseCases {

    private final GerarDashboardOperacionalUseCase dashboardOperacional;
    private final GerarDashboardTurnosUseCase dashboardTurnos;
    private final ConsultarTurnosUseCase consultaTurnos;
    private final DetalharTurnoUseCase detalhamentoTurno;
    private final ContextualizarExecucaoUseCase contextoExecucao;
    private final ConsultarNotasDoRecorteUseCase notasDoRecorte;

    public DashboardOperacionalResponse gerarDashboardOperacional(DashboardOperacionalFilter filter) {
        return dashboardOperacional.executar(filter);
    }

    public DashboardTurnosResponse gerarDashboardTurnos(DashboardTurnosFilter filter) {
        return dashboardTurnos.executar(filter);
    }

    public PageResponse<TurnoConsultaResponse> consultarTurnos(ConsultaTurnosFilter filter, Pageable pageable) {
        return consultaTurnos.executar(filter, pageable);
    }

    public PageResponse<NotaDoRecorteResponse> consultarNotas(NotasDoRecorteFilter filter, Pageable pageable) {
        return notasDoRecorte.executar(filter, pageable);
    }

    public DetalhamentoTurnoResponse detalharTurno(UUID idTurno) {
        return detalhamentoTurno.executar(idTurno);
    }

    public ContextoExecucaoResponse contextualizarExecucao(UUID idExecucao) {
        return contextoExecucao.executar(idExecucao);
    }
}
