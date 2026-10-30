package com.nexo.app.modules.rh.doc;

import com.nexo.app.modules.rh.SupervisorResponse;
import com.nexo.app.modules.rh.application.dto.*;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Supervisores", description = "Gestão de supervisores vinculados a coordenadores e períodos de vigência")
@SecurityRequirement(name = "bearerAuth")
public interface SupervisorControllerDoc {

    @Operation(summary = "Registrar novo supervisor")
    ResponseEntity<SupervisorResponse> criar(SupervisorRequest request);

    @Operation(summary = "Validar importação de supervisores via CSV", description = "Processa o arquivo CSV e retorna os registros validados antes da persistência.")
    ResponseEntity<ImportacaoSupervisorResponse> validarImportacao(
            @Parameter(description = "Arquivo CSV (separador ;)", content = @Content(mediaType = "multipart/form-data"))
            MultipartFile arquivo
    );

    @Operation(summary = "Confirmar importação de supervisores", description = "Persiste os registros validados previamente.")
    ResponseEntity<ImportacaoSupervisorResponse> confirmarImportacao(ConfirmarImportacaoSupervisorRequest request);

    @Operation(summary = "Atualizar supervisor existente")
    ResponseEntity<SupervisorResponse> atualizar(UUID id, SupervisorRequest request);

    @Operation(summary = "Desativar supervisor (Soft Delete)")
    ResponseEntity<Void> desativar(UUID id);

    @Operation(summary = "Ativar supervisor")
    ResponseEntity<Void> ativar(UUID id);

    @Operation(summary = "Buscar supervisor por ID")
    ResponseEntity<SupervisorResponse> buscarPorId(UUID id);

    @Operation(summary = "Listar supervisores com filtros dinâmicos")
    ResponseEntity<PageResponse<SupervisorResponse>> listar(@ParameterObject SupervisorFilter filter, @ParameterObject Pageable pageable);

    @Operation(summary = "Listar supervisores ativos simplificados para dropdowns")
    ResponseEntity<List<SupervisorMinResponse>> listarAtivosMin();

    @Operation(summary = "Baixar modelo CSV para importação de supervisores", description = "Disponibiliza o arquivo CSV de exemplo com cabeçalho e formato exatos para importação.")
    ResponseEntity<byte[]> baixarModeloCsv();
}
