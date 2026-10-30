package com.nexo.app.modules.rh.doc;

import com.nexo.app.modules.rh.FuncaoResponse;
import com.nexo.app.modules.rh.application.dto.*;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(name = "Funções", description = "Gestão de cargos e papéis administrativos/operacionais")
@SecurityRequirement(name = "bearerAuth")
public interface FuncaoControllerDoc {

    @Operation(summary = "Registrar nova função")
    ResponseEntity<FuncaoResponse> criar(FuncaoRequest request);

    @Operation(summary = "Validar importação de funções via CSV", description = "Processa o arquivo CSV e retorna os registros validados antes da persistência.")
    ResponseEntity<ImportacaoFuncaoResponse> validarImportacao(
            @Parameter(description = "Arquivo CSV (separador ;)", content = @Content(mediaType = "multipart/form-data"))
            MultipartFile arquivo
    );

    @Operation(summary = "Confirmar importação de funções", description = "Persiste os registros que foram validados previamente.")
    ResponseEntity<ImportacaoFuncaoResponse> confirmarImportacao(ConfirmarImportacaoFuncaoRequest request);

    @Operation(summary = "Atualizar função existente")
    ResponseEntity<FuncaoResponse> atualizar(UUID id, FuncaoRequest request);

    @Operation(summary = "Desativar função (Soft Delete)")
    ResponseEntity<Void> desativar(UUID id);

    @Operation(summary = "Ativar função")
    ResponseEntity<Void> ativar(UUID id);

    @Operation(summary = "Buscar função por ID")
    ResponseEntity<FuncaoResponse> buscarPorId(UUID id);

    @Operation(summary = "Listar funções com filtros dinâmicos")
    ResponseEntity<PageResponse<FuncaoResponse>> listar(@ParameterObject FuncaoFilter filter, @ParameterObject Pageable pageable);

    @Operation(summary = "Baixar modelo CSV para importação de funções", description = "Disponibiliza o arquivo CSV de exemplo com cabeçalho e formato exatos para importação.")
    ResponseEntity<byte[]> baixarModeloCsv();
}
