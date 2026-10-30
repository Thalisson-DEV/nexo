package com.nexo.app.modules.obras.doc;

import com.nexo.app.modules.obras.application.dto.CarteiraObraRequest;
import com.nexo.app.modules.obras.application.dto.CarteiraObraResponse;
import com.nexo.app.modules.obras.application.dto.HistoricoStatusObraResponse;
import com.nexo.app.modules.obras.application.dto.IndicadoresCarteiraResponse;
import com.nexo.app.modules.obras.application.dto.ObraFilter;
import com.nexo.app.modules.obras.application.dto.ObraProximaResponse;
import com.nexo.app.modules.obras.application.dto.OrcamentoObraResponse;
import com.nexo.app.modules.obras.application.dto.StatusOperacionalObraRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

@Tag(name = "Obras - Carteira", description = "Dados de carteira, status operacional, deslocamento da base, obras próximas e indicadores")
@SecurityRequirement(name = "bearerAuth")
public interface CarteiraObraControllerDoc {

    @Operation(summary = "Consultar os dados de carteira da obra")
    ResponseEntity<CarteiraObraResponse> buscar(UUID id);

    @Operation(summary = "Informar os dados de carteira da obra",
            description = "A classe de tensão guarda quem informou e quando; informar a mesma classe não altera o registro.")
    ResponseEntity<CarteiraObraResponse> informar(UUID id, CarteiraObraRequest request);

    @Operation(summary = "Informar o status operacional da obra com observação (fica no histórico)")
    ResponseEntity<CarteiraObraResponse> informarStatus(UUID id, StatusOperacionalObraRequest request);

    @Operation(summary = "Listar o histórico de status da obra, mais recente primeiro")
    ResponseEntity<List<HistoricoStatusObraResponse>> listarHistoricoStatus(UUID id);

    @Operation(summary = "Recalcular agora o deslocamento da base até a obra")
    ResponseEntity<CarteiraObraResponse> recalcularDeslocamento(UUID id);

    @Operation(summary = "Consultar o orçamento previsto importado da obra (serviços e totais)")
    ResponseEntity<OrcamentoObraResponse> buscarOrcamento(UUID id);

    @Operation(summary = "Listar obras ativas no raio da obra (linha reta)")
    ResponseEntity<List<ObraProximaResponse>> listarObrasProximas(UUID id,
            @Parameter(description = "Raio em km (padrão 10, máximo 100)") Double raioKm);

    @Operation(summary = "Indicadores do recorte filtrado da listagem de obras")
    ResponseEntity<IndicadoresCarteiraResponse> indicadores(@ParameterObject ObraFilter filter);
}
