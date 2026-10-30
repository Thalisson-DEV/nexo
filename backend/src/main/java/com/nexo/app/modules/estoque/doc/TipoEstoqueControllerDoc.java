package com.nexo.app.modules.estoque.doc;

import com.nexo.app.modules.estoque.application.dto.*;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(name = "Tipos de Estoque", description = "Gestão de tipos de estoque (Ex: FISICO, CAMPO)")
@SecurityRequirement(name = "bearerAuth")
public interface TipoEstoqueControllerDoc {
    @Operation(summary = "Criar novo tipo")
    ResponseEntity<TipoEstoqueResponse> criar(TipoEstoqueRequest request);

    @Operation(summary = "Importar via CSV")
    ResponseEntity<ImportacaoTipoEstoqueResponse> importar(MultipartFile arquivo);

    @Operation(summary = "Confirmar importação")
    ResponseEntity<ImportacaoTipoEstoqueResponse> confirmarImportacao(ConfirmarImportacaoTipoEstoqueRequest request);

    @Operation(summary = "Atualizar tipo")

    ResponseEntity<TipoEstoqueResponse> atualizar(UUID id, TipoEstoqueRequest request);

    @Operation(summary = "Listar tipos de estoque com filtros")
    ResponseEntity<PageResponse<TipoEstoqueResponse>> listar(@ParameterObject TipoEstoqueFilter filter, @ParameterObject Pageable pageable);

    @Operation(summary = "Buscar por ID")
    ResponseEntity<TipoEstoqueResponse> buscarPorId(UUID id);

    @Operation(summary = "Ativar")
    ResponseEntity<Void> ativar(UUID id);

    @Operation(summary = "Desativar")
    ResponseEntity<Void> desativar(UUID id);

    @Operation(summary = "Baixar modelo CSV para importação de tipos de estoque", description = "Disponibiliza o arquivo CSV de exemplo com cabeçalho e formato exatos para importação.")
    ResponseEntity<byte[]> baixarModeloCsv();
}
