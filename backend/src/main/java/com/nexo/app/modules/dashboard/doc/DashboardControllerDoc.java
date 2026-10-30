package com.nexo.app.modules.dashboard.doc;

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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

@Tag(name = "Dashboards", description = "Dashboards, consulta de turnos e relatórios das equipes de campo")
public interface DashboardControllerDoc {

    @Operation(summary = "Produção das equipes no período, comparada à meta proporcional e ao ritmo")
    ResponseEntity<DashboardOperacionalResponse> gerarDashboardOperacional(
            @ParameterObject DashboardOperacionalFilter filter
    );

    @Operation(summary = "Situação dos turnos das equipes em um dia")
    ResponseEntity<DashboardTurnosResponse> gerarDashboardTurnos(
            @ParameterObject DashboardTurnosFilter filter
    );

    @Operation(summary = "Relatório de um turno: composição, notas, indicadores e histórico da equipe")
    ResponseEntity<DetalhamentoTurnoResponse> detalharTurno(UUID idTurno);

    @Operation(summary = "Consulta paginada de turnos com a produção de cada um",
            description = "Ordena por dataInicio, dataFim ou status; outras colunas são ignoradas.")
    ResponseEntity<PageResponse<TurnoConsultaResponse>> consultarTurnos(
            @ParameterObject ConsultaTurnosFilter filter,
            @ParameterObject Pageable pageable
    );

    @Operation(summary = "Notas concluídas que compõem os números dos dashboards de execução",
            description = "Mesmo escopo dos dashboards (equipes ativas do recorte, dia civil de São Paulo). "
                    + "Ordena por dataLancamento ou valorTotalFaturado; páginas de até 500 notas.")
    ResponseEntity<PageResponse<NotaDoRecorteResponse>> consultarNotas(
            @ParameterObject NotasDoRecorteFilter filter,
            @ParameterObject Pageable pageable
    );

    @Operation(summary = "Contexto de uma nota: turno, dia da equipe, média da equipe e valor das ações")
    ResponseEntity<ContextoExecucaoResponse> contextualizarExecucao(UUID idExecucao);
}
