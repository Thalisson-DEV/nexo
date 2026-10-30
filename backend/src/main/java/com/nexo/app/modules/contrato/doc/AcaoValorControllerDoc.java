package com.nexo.app.modules.contrato.doc;

import com.nexo.app.modules.contrato.application.dto.*;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(name = "Ações Valores", description = "Gestão de valores das ações por contrato e vigência")
@SecurityRequirement(name = "bearerAuth")
public interface AcaoValorControllerDoc {

    @Operation(summary = "Definir valor para uma ação em um contrato")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Valor definido com sucesso"),
        @ApiResponse(responseCode = "400", description = "Dados inválidos")
    })
    ResponseEntity<AcaoValorResponse> criar(AcaoValorRequest request);

    @Operation(summary = "Atualizar valor de uma ação")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Valor atualizado com sucesso"),
        @ApiResponse(responseCode = "404", description = "Valor não encontrado")
    })
    ResponseEntity<AcaoValorResponse> atualizar(UUID id, AcaoValorRequest request);

    @Operation(summary = "Buscar valor de ação por ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Valor encontrado"),
        @ApiResponse(responseCode = "404", description = "Valor não encontrado")
    })
    ResponseEntity<AcaoValorResponse> buscarPorId(UUID id);

    @Operation(summary = "Listar valores de ações de forma paginada")
    ResponseEntity<PageResponse<AcaoValorResponse>> listar(@ParameterObject AcaoValorFilter filter, Pageable pageable);

    @Operation(summary = "Desativar valor de uma ação (Soft Delete)")
    @ApiResponse(responseCode = "204", description = "Valor desativado com sucesso")
    ResponseEntity<Void> desativar(UUID id);

    @Operation(summary = "Ativar valor de uma ação")
    @ApiResponse(responseCode = "200", description = "Valor desativado com sucesso")
    ResponseEntity<Void> ativar(UUID id);

    @Operation(summary = "Validar arquivo CSV para importação de valores de ações")
    ResponseEntity<ImportacaoAcaoValorResponse> importar(MultipartFile arquivo);

    @Operation(summary = "Confirmar importação de valores de ações")
    ResponseEntity<ImportacaoAcaoValorResponse> confirmarImportacao(ConfirmarImportacaoAcaoValorRequest request);

    @Operation(summary = "Baixar modelo CSV para importação de valores de ações", description = "Disponibiliza o arquivo CSV de exemplo com cabeçalho e formato exatos para importação.")
    ResponseEntity<byte[]> baixarModeloCsv();
}
