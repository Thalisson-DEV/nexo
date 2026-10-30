package com.nexo.app.modules.frota.doc;

import com.nexo.app.modules.frota.application.dto.AcoplamentoImplementoResponse;
import com.nexo.app.modules.frota.application.dto.AcoplarImplementoRequest;
import com.nexo.app.modules.frota.application.dto.EncerrarPeriodoRequest;
import com.nexo.app.modules.frota.application.dto.ImplementoFilter;
import com.nexo.app.modules.frota.application.dto.ImplementoRequest;
import com.nexo.app.modules.frota.application.dto.ImplementoResponse;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

@Tag(name = "Frota — Implementos", description = "Implementos e histórico de acoplamento a veículos")
public interface ImplementoFrotaControllerDoc {

    @Operation(summary = "Listar implementos")
    ResponseEntity<PageResponse<ImplementoResponse>> listar(ImplementoFilter filtro, Pageable pageable);

    @Operation(summary = "Buscar implemento")
    ResponseEntity<ImplementoResponse> buscar(UUID id);

    @Operation(summary = "Cadastrar implemento")
    ResponseEntity<ImplementoResponse> criar(ImplementoRequest request);

    @Operation(summary = "Atualizar implemento")
    ResponseEntity<ImplementoResponse> atualizar(UUID id, ImplementoRequest request);

    @Operation(summary = "Desativar implemento", description = "Exige que o implemento esteja desacoplado.")
    ResponseEntity<Void> desativar(UUID id);

    @Operation(summary = "Reativar implemento")
    ResponseEntity<Void> ativar(UUID id);

    @Operation(summary = "Acoplar a um veículo", description = "Se estava em outro veículo, encerra lá no mesmo instante.")
    ResponseEntity<AcoplamentoImplementoResponse> acoplar(UUID id, AcoplarImplementoRequest request);

    @Operation(summary = "Desacoplar")
    ResponseEntity<AcoplamentoImplementoResponse> desacoplar(UUID id, EncerrarPeriodoRequest request);

    @Operation(summary = "Histórico de acoplamentos do implemento")
    ResponseEntity<List<AcoplamentoImplementoResponse>> historico(UUID id);
}
