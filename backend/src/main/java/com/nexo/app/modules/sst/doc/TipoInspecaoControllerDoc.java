package com.nexo.app.modules.sst.doc;

import com.nexo.app.modules.sst.application.dto.TipoInspecaoFilter;
import com.nexo.app.modules.sst.application.dto.TipoInspecaoRequest;
import com.nexo.app.modules.sst.application.dto.TipoInspecaoResponse;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

@Tag(name = "SST - Tipos de Inspeção", description = "Parametrização de tipos/modalidades de inspeção de segurança operacional")
@SecurityRequirement(name = "bearerAuth")
public interface TipoInspecaoControllerDoc {

    @Operation(summary = "Cadastrar um novo tipo de inspeção",
            description = "O nome é único entre os tipos ativos, comparado sem acento e em minúsculas.")
    @ApiResponse(responseCode = "201", description = "Tipo de inspeção cadastrado")
    @ApiResponse(responseCode = "422", description = "Nome duplicado ou inválido")
    ResponseEntity<TipoInspecaoResponse> criar(TipoInspecaoRequest request);

    @Operation(summary = "Buscar tipo de inspeção por ID")
    ResponseEntity<TipoInspecaoResponse> buscarPorId(UUID id);

    @Operation(summary = "Listar tipos de inspeção com filtros dinâmicos e paginação")
    ResponseEntity<PageResponse<TipoInspecaoResponse>> listar(
            @ParameterObject TipoInspecaoFilter filter, @ParameterObject Pageable pageable);

    @Operation(summary = "Listar tipos de inspeção ativos",
            description = "Sem paginação, em ordem de exibição configurada — abastece os seletores de formulário e metas.")
    ResponseEntity<List<TipoInspecaoResponse>> listarOpcoes();

    @Operation(summary = "Atualizar tipo de inspeção")
    @ApiResponse(responseCode = "422", description = "Nome duplicado ou tipo inativo")
    ResponseEntity<TipoInspecaoResponse> atualizar(UUID id, TipoInspecaoRequest request);

    @Operation(summary = "Alternar status ativo/inativo do tipo de inspeção")
    ResponseEntity<Void> alternarStatus(UUID id);
}
