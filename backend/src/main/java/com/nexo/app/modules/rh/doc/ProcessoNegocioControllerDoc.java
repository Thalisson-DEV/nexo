package com.nexo.app.modules.rh.doc;

import com.nexo.app.modules.rh.ProcessoNegocioResponse;
import com.nexo.app.modules.rh.application.dto.*;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(name = "Processos de Negócio", description = "Gestão dos departamentos e divisões operacionais da empresa")
@SecurityRequirement(name = "bearerAuth")
public interface ProcessoNegocioControllerDoc {

    @Operation(summary = "Criar novo processo de negócio")
    ResponseEntity<ProcessoNegocioResponse> criar(ProcessoNegocioRequest request);

    @Operation(summary = "Atualizar processo de negócio")
    ResponseEntity<ProcessoNegocioResponse> atualizar(UUID id, ProcessoNegocioRequest request);

    @Operation(summary = "Desativar processo de negócio (Soft Delete)")
    ResponseEntity<Void> desativar(UUID id);

    @Operation(summary = "Ativar processo de negócio")
    ResponseEntity<Void> ativar(UUID id);

    @Operation(summary = "Buscar processo por ID")
    ResponseEntity<ProcessoNegocioResponse> buscarPorId(UUID id);

    @Operation(summary = "Listar todos os processos de negócio com filtros dinâmicos")
    ResponseEntity<PageResponse<ProcessoNegocioResponse>> listar(@ParameterObject ProcessoNegocioFilter filter, @ParameterObject Pageable pageable);

    @Operation(summary = "Validar arquivo CSV para importação de processos de negócio")
    ResponseEntity<ImportacaoProcessoNegocioResponse> importar(MultipartFile arquivo);

    @Operation(summary = "Confirmar importação de processos de negócio")
    ResponseEntity<ImportacaoProcessoNegocioResponse> confirmarImportacao(ConfirmarImportacaoProcessoNegocioRequest request);

    @Operation(summary = "Baixar modelo CSV para importação de processos de negócio", description = "Disponibiliza o arquivo CSV de exemplo com cabeçalho e formato exatos para importação.")
    ResponseEntity<byte[]> baixarModeloCsv();
}
