package com.nexo.app.modules.obras.doc;

import com.nexo.app.modules.obras.application.dto.TipoAnexoObraFilter;
import com.nexo.app.modules.obras.application.dto.TipoAnexoObraRequest;
import com.nexo.app.modules.obras.application.dto.TipoAnexoObraResponse;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

@Tag(name = "Obras - Tipos de Anexos", description = "Gestão de tipos de anexos técnicos e documentais de obras")
@SecurityRequirement(name = "bearerAuth")
public interface TipoAnexoObraControllerDoc {

    @Operation(summary = "Cadastrar novo tipo de anexo de obra")
    ResponseEntity<TipoAnexoObraResponse> criar(TipoAnexoObraRequest request);

    @Operation(summary = "Atualizar tipo de anexo de obra")
    ResponseEntity<TipoAnexoObraResponse> atualizar(UUID id, TipoAnexoObraRequest request);

    @Operation(summary = "Buscar tipo de anexo de obra por ID")
    ResponseEntity<TipoAnexoObraResponse> buscarPorId(UUID id);

    @Operation(summary = "Listar tipos de anexo de obra com paginação e filtros")
    ResponseEntity<PageResponse<TipoAnexoObraResponse>> listar(@ParameterObject TipoAnexoObraFilter filter, @ParameterObject Pageable pageable);

    @Operation(summary = "Listar todos os tipos de anexo ativos")
    ResponseEntity<List<TipoAnexoObraResponse>> listarTodosAtivos();

    @Operation(summary = "Desativar tipo de anexo de obra (Soft Delete)")
    ResponseEntity<Void> desativar(UUID id);
}
