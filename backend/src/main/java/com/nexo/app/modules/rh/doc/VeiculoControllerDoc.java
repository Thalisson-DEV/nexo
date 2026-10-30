package com.nexo.app.modules.rh.doc;

import com.nexo.app.modules.rh.VeiculoResponse;
import com.nexo.app.modules.rh.application.dto.*;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(name = "Veículos", description = "Gestão da frota operacional")
@SecurityRequirement(name = "bearerAuth")
public interface VeiculoControllerDoc {

    @Operation(summary = "Cadastrar novo veículo")
    ResponseEntity<VeiculoResponse> criar(@Valid @RequestBody VeiculoRequest request);

    @Operation(summary = "Validar arquivo CSV de importação de veículos")
    @ApiResponse(responseCode = "200", description = "Arquivo validado com sucesso")
    ResponseEntity<ImportacaoVeiculoResponse> importar(MultipartFile arquivo);

    @Operation(summary = "Confirmar importação de veículos após validação")
    @ApiResponse(responseCode = "200", description = "Importação iniciada com sucesso")
    ResponseEntity<ImportacaoVeiculoResponse> confirmarImportacao(ConfirmarImportacaoVeiculoRequest request);

    @Operation(summary = "Buscar veículo por ID")
    ResponseEntity<VeiculoResponse> buscarPorId(@PathVariable UUID id);

    @Operation(summary = "Listar veículos com filtros dinâmicos", description = "Permite filtrar por placa, modelo, base e status")
    ResponseEntity<PageResponse<VeiculoResponse>> listar(@ParameterObject VeiculoFilter filter, @ParameterObject Pageable pageable);

    @Operation(summary = "Atualizar dados do veículo")
    ResponseEntity<VeiculoResponse> atualizar(@PathVariable UUID id, @Valid @RequestBody VeiculoRequest request);

    @Operation(summary = "Desativar um veículo")
    ResponseEntity<Void> desativar(@PathVariable UUID id);

    @Operation(summary = "Ativar um veículo")
    ResponseEntity<Void> ativar(@PathVariable UUID id);

    @Operation(summary = "Baixar modelo CSV para importação de veículos", description = "Disponibiliza o arquivo CSV de exemplo com cabeçalho e formato exatos para importação.")
    ResponseEntity<byte[]> baixarModeloCsv();
}
