package com.nexo.app.modules.obras.doc;

import com.nexo.app.modules.obras.application.dto.ModeloChecklistObraFilter;
import com.nexo.app.modules.obras.application.dto.ModeloChecklistObraRequest;
import com.nexo.app.modules.obras.application.dto.ModeloChecklistObraResponse;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

@Tag(name = "Obras - Modelos de checklist", description = "Checklists configuráveis da visita prévia e da ficha As Built")
@SecurityRequirement(name = "bearerAuth")
public interface ModeloChecklistObraControllerDoc {

    @Operation(summary = "Criar modelo de checklist")
    ResponseEntity<ModeloChecklistObraResponse> criar(ModeloChecklistObraRequest request);

    @Operation(summary = "Alterar modelo; depois de usado, só título e descrição mudam")
    ResponseEntity<ModeloChecklistObraResponse> atualizar(UUID id, ModeloChecklistObraRequest request);

    @Operation(summary = "Buscar modelo por ID")
    ResponseEntity<ModeloChecklistObraResponse> buscarPorId(UUID id);

    @Operation(summary = "Listar modelos com paginação e filtros")
    ResponseEntity<PageResponse<ModeloChecklistObraResponse>> listar(@ParameterObject ModeloChecklistObraFilter filter, @ParameterObject Pageable pageable);

    @Operation(summary = "Duplicar modelo para mudar as perguntas")
    ResponseEntity<ModeloChecklistObraResponse> duplicar(UUID id);

    @Operation(summary = "Desativar modelo")
    ResponseEntity<Void> desativar(UUID id);

    @Operation(summary = "Reativar modelo")
    ResponseEntity<Void> ativar(UUID id);
}
