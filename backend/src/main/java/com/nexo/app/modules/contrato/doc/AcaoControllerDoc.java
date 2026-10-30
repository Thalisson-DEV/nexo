package com.nexo.app.modules.contrato.doc;

import com.nexo.app.modules.contrato.AcaoResponse;
import com.nexo.app.modules.contrato.application.dto.*;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(name = "Ações", description = "Gestão de ações comerciais (serviços)")
@SecurityRequirement(name = "bearerAuth")
public interface AcaoControllerDoc {

    @Operation(summary = "Criar uma nova ação")
    @ApiResponse(responseCode = "201", description = "Ação criada com sucesso")
    ResponseEntity<AcaoResponse> criar(AcaoRequest request);

    @Operation(summary = "Atualizar uma ação existente")
    ResponseEntity<AcaoResponse> atualizar(UUID id, AcaoRequest request);

    @Operation(summary = "Buscar uma ação por ID")
    ResponseEntity<AcaoResponse> buscarPorId(UUID id);

    @Operation(summary = "Listar ações de forma paginada")
    ResponseEntity<PageResponse<AcaoResponse>> listar(@ParameterObject AcaoFilter filter, Pageable pageable);

    @Operation(summary = "Desativar uma ação (Soft Delete)")
    ResponseEntity<Void> desativar(UUID id);

    @Operation(summary = "Ativar ação")
    ResponseEntity<Void> ativar(UUID id);

    @Operation(summary = "Validar arquivo CSV para importação de ações")
    ResponseEntity<ImportacaoAcaoResponse> importar(MultipartFile arquivo);

    @Operation(summary = "Confirmar importação de ações")
    ResponseEntity<ImportacaoAcaoResponse> confirmarImportacao(ConfirmarImportacaoAcaoRequest request);

    @Operation(summary = "Baixar modelo CSV para importação de ações", description = "Disponibiliza o arquivo CSV de exemplo com cabeçalho e formato exatos para importação.")
    ResponseEntity<byte[]> baixarModeloCsv();
}
