package com.nexo.app.modules.obras.doc;

import com.nexo.app.modules.obras.application.dto.AgenteResponsavelFilter;
import com.nexo.app.modules.obras.application.dto.AgenteResponsavelRequest;
import com.nexo.app.modules.obras.application.dto.AgenteResponsavelResponse;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

@Tag(name = "Obras - Agentes Responsáveis", description = "Gestão de fiscais e agentes da concessionária/cliente")
@SecurityRequirement(name = "bearerAuth")
public interface AgenteResponsavelControllerDoc {

    @Operation(summary = "Cadastrar novo agente responsável")
    ResponseEntity<AgenteResponsavelResponse> criar(AgenteResponsavelRequest request);

    @Operation(summary = "Atualizar agente responsável")
    ResponseEntity<AgenteResponsavelResponse> atualizar(UUID id, AgenteResponsavelRequest request);

    @Operation(summary = "Buscar agente responsável por ID")
    ResponseEntity<AgenteResponsavelResponse> buscarPorId(UUID id);

    @Operation(summary = "Listar agentes responsáveis com paginação e filtros")
    ResponseEntity<PageResponse<AgenteResponsavelResponse>> listar(@ParameterObject AgenteResponsavelFilter filter, @ParameterObject Pageable pageable);

    @Operation(summary = "Listar todos os agentes responsáveis ativos")
    ResponseEntity<List<AgenteResponsavelResponse>> listarTodosAtivos();

    @Operation(summary = "Desativar agente responsável (Soft Delete)")
    ResponseEntity<Void> desativar(UUID id);
}
