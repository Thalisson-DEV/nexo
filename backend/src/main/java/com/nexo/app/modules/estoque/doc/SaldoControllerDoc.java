package com.nexo.app.modules.estoque.doc;

import com.nexo.app.modules.estoque.application.dto.SaldoFilter;
import com.nexo.app.modules.estoque.application.dto.SaldoResponse;
import com.nexo.app.modules.estoque.application.dto.SaldoSerieFilter;
import com.nexo.app.modules.estoque.application.dto.SaldoSerieResponse;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

@Tag(name = "Saldos", description = "Consulta de inventário (Read-only)")
@SecurityRequirement(name = "bearerAuth")
public interface SaldoControllerDoc {

    @Operation(summary = "Listar saldos com filtros (Por Estoque, Por Material, negativos, etc)")
    ResponseEntity<PageResponse<SaldoResponse>> listar(@ParameterObject SaldoFilter filter, @ParameterObject Pageable pageable);

    @Operation(summary = "Listar números de série ativos em um estoque/equipe")
    ResponseEntity<PageResponse<SaldoSerieResponse>> listarSeriais(@ParameterObject SaldoSerieFilter filter, @ParameterObject Pageable pageable);

    @Operation(summary = "Conferir séries já cadastradas",
               description = "Recebe até 2000 números de série e devolve os que já estão ativos em algum estoque, " +
                             "com estoque e material. Usado antes de uma entrada avulsa, que recusa série repetida no sistema.")
    ResponseEntity<java.util.List<SaldoSerieResponse>> verificarSeries(
            com.nexo.app.modules.estoque.application.dto.VerificarSeriesRequest request);

    @Operation(summary = "Buscar saldo por ID")
    ResponseEntity<SaldoResponse> buscarPorId(UUID id);
}
