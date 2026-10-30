package com.nexo.app.modules.iam.doc;

import com.nexo.app.modules.iam.application.dto.PermissaoFilter;
import com.nexo.app.modules.iam.application.dto.PermissaoRequest;
import com.nexo.app.modules.iam.application.dto.PermissaoResponse;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

@Tag(name = "Permissões", description = "Gestão de permissões granulares do sistema")
@SecurityRequirement(name = "bearerAuth")
public interface PermissaoControllerDoc {

    @Operation(summary = "Criar nova permissão")
    @ApiResponse(responseCode = "201", description = "Permissão criada com sucesso")
    ResponseEntity<PermissaoResponse> criar(PermissaoRequest request);

    @Operation(summary = "Atualizar permissão existente")
    ResponseEntity<PermissaoResponse> atualizar(UUID id, PermissaoRequest request);

    @Operation(summary = "Ativar/Desativar permissão")
    ResponseEntity<Void> alterarStatus(UUID id, boolean ativo);

    @Operation(summary = "Buscar permissão por ID")
    ResponseEntity<PermissaoResponse> buscarPorId(UUID id);

    @Operation(summary = "Listar permissões paginadas")
    ResponseEntity<PageResponse<PermissaoResponse>> listar(@ParameterObject PermissaoFilter filter, @ParameterObject Pageable pageable);
}
