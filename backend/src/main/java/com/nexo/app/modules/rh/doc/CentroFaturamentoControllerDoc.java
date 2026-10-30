package com.nexo.app.modules.rh.doc;

import com.nexo.app.modules.rh.application.dto.CentroFaturamentoFilter;
import com.nexo.app.modules.rh.application.dto.CentroFaturamentoRequest;
import com.nexo.app.modules.rh.CentroFaturamentoResponse;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@Tag(name = "Centros de Faturamento", description = "Gestão de Regionais Comerciais")
@SecurityRequirement(name = "bearerAuth")
public interface CentroFaturamentoControllerDoc {

    @Operation(summary = "Criar novo Centro de Faturamento")
    ResponseEntity<CentroFaturamentoResponse> criar(@Valid @RequestBody CentroFaturamentoRequest request);

    @Operation(summary = "Buscar Centro de Faturamento por ID")
    ResponseEntity<CentroFaturamentoResponse> buscarPorId(@PathVariable UUID id);

    @Operation(summary = "Listar Centros de Faturamento com filtros dinâmicos")
    ResponseEntity<PageResponse<CentroFaturamentoResponse>> listar(@ParameterObject CentroFaturamentoFilter filter, @ParameterObject Pageable pageable);

    @Operation(summary = "Atualizar Centro de Faturamento")
    ResponseEntity<CentroFaturamentoResponse> atualizar(@PathVariable UUID id, @Valid @RequestBody CentroFaturamentoRequest request);

    @Operation(summary = "Desativar um Centro de Faturamento")
    ResponseEntity<Void> desativar(@PathVariable UUID id);

    @Operation(summary = "Ativar um Centro de Faturamento")
    ResponseEntity<Void> ativar(@PathVariable UUID id);
}
