package com.nexo.app.modules.frota.doc;

import com.nexo.app.modules.frota.application.dto.JustificativaRequest;
import com.nexo.app.modules.frota.application.dto.LeituraOdometroFilter;
import com.nexo.app.modules.frota.application.dto.LeituraOdometroResponse;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

@Tag(name = "Frota — Odômetro", description = "Livro-razão de quilometragem e fila de inconsistências")
public interface OdometroFrotaControllerDoc {

    @Operation(summary = "Consultar leituras", description = "Use situacoes=INCONSISTENTE para a fila de conferência.")
    ResponseEntity<PageResponse<LeituraOdometroResponse>> listar(LeituraOdometroFilter filtro, Pageable pageable);

    @Operation(summary = "Anular leitura", description = "Tira a leitura do cálculo de KM, guardando quem e por quê.")
    ResponseEntity<LeituraOdometroResponse> anular(UUID id, JustificativaRequest request);

    @Operation(summary = "Confirmar leitura inconsistente", description = "O gestor conferiu e considera a leitura correta.")
    ResponseEntity<LeituraOdometroResponse> confirmar(UUID id, JustificativaRequest request);
}
