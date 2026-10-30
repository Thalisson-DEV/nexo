package com.nexo.app.modules.chamado.doc;

import com.nexo.app.modules.chamado.application.dto.DashboardChamadosFilter;
import com.nexo.app.modules.chamado.application.dto.DashboardChamadosResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

@Tag(name = "Chamados - Dashboards", description = "Endpoints analíticos e gerenciais do módulo de chamados")
public interface DashboardChamadosControllerDoc {

    @Operation(summary = "Gera o dashboard gerencial e analítico de chamados",
            description = "Devolve indicadores consolidados, taxas de resolução e SLA, aging do backlog, rankings por tópico, equipe e atendente, e série temporal.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dashboard gerencial gerado com sucesso",
                    content = @Content(schema = @Schema(implementation = DashboardChamadosResponse.class))),
            @ApiResponse(responseCode = "400", description = "Parâmetros de filtro ou período inválidos",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Usuário não possui autorização",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<DashboardChamadosResponse> dashboard(@ParameterObject DashboardChamadosFilter filter);
}
