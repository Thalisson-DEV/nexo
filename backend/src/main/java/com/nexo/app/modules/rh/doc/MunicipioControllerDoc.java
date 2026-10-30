package com.nexo.app.modules.rh.doc;

import com.nexo.app.modules.rh.MunicipioResponse;
import com.nexo.app.modules.rh.application.dto.*;
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

@Tag(name = "Municípios", description = "Gestão de municípios de atuação")
@SecurityRequirement(name = "bearerAuth")
public interface MunicipioControllerDoc {

    @Operation(summary = "Criar um novo município")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Município criado com sucesso"),
        @ApiResponse(responseCode = "400", description = "Dados inválidos"),
        @ApiResponse(responseCode = "401", description = "Não autorizado")
    })
    ResponseEntity<MunicipioResponse> criar(MunicipioRequest request);

    @Operation(summary = "Atualizar um município existente")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Município atualizado com sucesso"),
        @ApiResponse(responseCode = "404", description = "Município não encontrado")
    })
    ResponseEntity<MunicipioResponse> atualizar(UUID id, MunicipioRequest request);

    @Operation(summary = "Buscar município por ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Município encontrado"),
        @ApiResponse(responseCode = "404", description = "Município não encontrado")
    })
    ResponseEntity<MunicipioResponse> buscarPorId(UUID id);

    @Operation(summary = "Listar municípios de forma paginada")
    ResponseEntity<PageResponse<MunicipioResponse>> listar(@ParameterObject MunicipioFilter filter, Pageable pageable);

    @Operation(summary = "Desativar um município (Soft Delete)")
    @ApiResponse(responseCode = "204", description = "Município desativado com sucesso")
    ResponseEntity<Void> desativar(UUID id);

    @Operation(summary = "Ativar um município")
    @ApiResponse(responseCode = "200", description = "Município ativado com sucesso")
    ResponseEntity<Void> ativar(UUID id);

    @Operation(summary = "Validar arquivo CSV para importação de municípios")
    ResponseEntity<ImportacaoMunicipioResponse> importar(MultipartFile arquivo);

    @Operation(summary = "Confirmar importação de municípios")
    ResponseEntity<ImportacaoMunicipioResponse> confirmarImportacao(ConfirmarImportacaoMunicipioRequest request);

    @Operation(summary = "Baixar modelo CSV para importação de municípios", description = "Disponibiliza o arquivo CSV de exemplo com cabeçalho e formato exatos para importação.")
    ResponseEntity<byte[]> baixarModeloCsv();
}
