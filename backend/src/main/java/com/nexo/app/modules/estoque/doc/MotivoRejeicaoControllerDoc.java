package com.nexo.app.modules.estoque.doc;

import com.nexo.app.modules.estoque.application.dto.MotivoRejeicaoFilter;
import com.nexo.app.modules.estoque.application.dto.MotivoRejeicaoRequest;
import com.nexo.app.modules.estoque.application.dto.MotivoRejeicaoResponse;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

@Tag(name = "Estoque - Motivos de Rejeição", description = "Gestão de motivos padronizados para rejeição de solicitações")
@SecurityRequirement(name = "bearerAuth")
public interface MotivoRejeicaoControllerDoc {
    @Operation(summary = "Criar novo motivo de rejeição")
    ResponseEntity<MotivoRejeicaoResponse> criar(MotivoRejeicaoRequest request);

    @Operation(summary = "Atualizar motivo de rejeição")
    ResponseEntity<MotivoRejeicaoResponse> atualizar(UUID id, MotivoRejeicaoRequest request);

    @Operation(summary = "Listar com filtros")
    ResponseEntity<PageResponse<MotivoRejeicaoResponse>> listar(@ParameterObject MotivoRejeicaoFilter filter, @ParameterObject Pageable pageable);

    @Operation(summary = "Buscar por ID")
    ResponseEntity<MotivoRejeicaoResponse> buscarPorId(UUID id);

    @Operation(summary = "Ativar")
    ResponseEntity<Void> ativar(UUID id);

    @Operation(summary = "Desativar")
    ResponseEntity<Void> desativar(UUID id);
}
