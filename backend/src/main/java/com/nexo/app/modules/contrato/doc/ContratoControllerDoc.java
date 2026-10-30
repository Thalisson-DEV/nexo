package com.nexo.app.modules.contrato.doc;

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

@Tag(name = "Contratos", description = "Gestão de contratos comerciais")
@SecurityRequirement(name = "bearerAuth")
public interface ContratoControllerDoc {

    @Operation(summary = "Criar um novo contrato")
    @ApiResponse(responseCode = "201", description = "Contrato criado com sucesso")
    ResponseEntity<ContratoResponse> criar(ContratoRequest request);

    @Operation(summary = "Atualizar um contrato existente")
    ResponseEntity<ContratoResponse> atualizar(UUID id, ContratoRequest request);

    @Operation(summary = "Buscar um contrato por ID")
    ResponseEntity<ContratoResponse> buscarPorId(UUID id);

    @Operation(summary = "Listar contratos de forma paginada")
    ResponseEntity<PageResponse<ContratoResponse>> listar(@ParameterObject ContratoFilter filter, Pageable pageable);

    @Operation(summary = "Desativar um contrato (Soft Delete)")
    ResponseEntity<Void> desativar(UUID id);

    @Operation(summary = "Ativar um contrato")
    ResponseEntity<Void> ativar(UUID id);

    @Operation(summary = "Validar arquivo CSV para importação de contratos")
    ResponseEntity<ImportacaoContratoResponse> importar(MultipartFile arquivo);

    @Operation(summary = "Confirmar importação de contratos")
    ResponseEntity<ImportacaoContratoResponse> confirmarImportacao(ConfirmarImportacaoContratoRequest request);

    @Operation(summary = "Baixar modelo CSV para importação de contratos", description = "Disponibiliza o arquivo CSV de exemplo com cabeçalho e formato exatos para importação.")
    ResponseEntity<byte[]> baixarModeloCsv();
}
