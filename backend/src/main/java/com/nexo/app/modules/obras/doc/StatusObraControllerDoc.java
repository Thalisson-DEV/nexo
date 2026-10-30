package com.nexo.app.modules.obras.doc;

import com.nexo.app.modules.obras.application.dto.StatusObraFilter;
import com.nexo.app.modules.obras.application.dto.StatusObraRequest;
import com.nexo.app.modules.obras.application.dto.StatusObraResponse;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

@Tag(name = "Obras - Status", description = "Gestão de status operacionais das obras")
@SecurityRequirement(name = "bearerAuth")
public interface StatusObraControllerDoc {

    @Operation(summary = "Cadastrar novo status de obra")
    ResponseEntity<StatusObraResponse> criar(StatusObraRequest request);

    @Operation(summary = "Atualizar status de obra")
    ResponseEntity<StatusObraResponse> atualizar(UUID id, StatusObraRequest request);

    @Operation(summary = "Buscar status de obra por ID")
    ResponseEntity<StatusObraResponse> buscarPorId(UUID id);

    @Operation(summary = "Listar status de obra com paginação e filtros")
    ResponseEntity<PageResponse<StatusObraResponse>> listar(@ParameterObject StatusObraFilter filter, @ParameterObject Pageable pageable);

    @Operation(summary = "Listar todos os status ativos ordenados por ordem de exibição")
    ResponseEntity<List<StatusObraResponse>> listarTodosAtivos();

    @Operation(summary = "Desativar status de obra (Soft Delete)")
    ResponseEntity<Void> desativar(UUID id);
}
