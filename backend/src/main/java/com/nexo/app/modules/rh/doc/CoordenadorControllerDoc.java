package com.nexo.app.modules.rh.doc;

import com.nexo.app.modules.rh.CoordenadorResponse;
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

@Tag(name = "Coordenadores", description = "Gestão de coordenadores e períodos de vigência")
@SecurityRequirement(name = "bearerAuth")
public interface CoordenadorControllerDoc {

    @Operation(summary = "Registrar novo coordenador")
    ResponseEntity<CoordenadorResponse> criar(CoordenadorRequest request);

    @Operation(summary = "Validar importação de coordenadores via CSV", description = "Processa o arquivo CSV e retorna os registros validados antes da persistência.")
    ResponseEntity<ImportacaoCoordenadorResponse> validarImportacao(
            @Parameter(description = "Arquivo CSV (separador ;)", content = @Content(mediaType = "multipart/form-data"))
            MultipartFile arquivo
    );

    @Operation(summary = "Confirmar importação de coordenadores", description = "Persiste os registros validados previamente.")
    ResponseEntity<ImportacaoCoordenadorResponse> confirmarImportacao(ConfirmarImportacaoCoordenadorRequest request);

    @Operation(summary = "Atualizar coordenador existente")
    ResponseEntity<CoordenadorResponse> atualizar(UUID id, CoordenadorRequest request);

    @Operation(summary = "Desativar coordenador (Soft Delete)")
    ResponseEntity<Void> desativar(UUID id);

    @Operation(summary = "Ativar coordenador")
    ResponseEntity<Void> ativar(UUID id);

    @Operation(summary = "Buscar coordenador por ID")
    ResponseEntity<CoordenadorResponse> buscarPorId(UUID id);

    @Operation(summary = "Listar coordenadores com filtros dinâmicos")
    ResponseEntity<PageResponse<CoordenadorResponse>> listar(@ParameterObject CoordenadorFilter filter, @ParameterObject Pageable pageable);

    @Operation(summary = "Listar coordenadores ativos simplificados para dropdowns")
    ResponseEntity<List<CoordenadorMinResponse>> listarAtivosMin();

    @Operation(summary = "Baixar modelo CSV para importação de coordenadores", description = "Disponibiliza o arquivo CSV de exemplo com cabeçalho e formato exatos para importação.")
    ResponseEntity<byte[]> baixarModeloCsv();
}
