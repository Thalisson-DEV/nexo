package com.nexo.app.modules.rh.doc;

import com.nexo.app.modules.rh.TipoEquipeResponse;
import com.nexo.app.modules.rh.application.dto.*;
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

@Tag(name = "Tipos de Equipe", description = "Gestão de tipos de equipes operacionais")
@SecurityRequirement(name = "bearerAuth")
public interface TipoEquipeControllerDoc {

    @Operation(summary = "Criar um novo tipo de equipe")
    @ApiResponse(responseCode = "201", description = "Tipo de equipe criado com sucesso")
    ResponseEntity<TipoEquipeResponse> criar(TipoEquipeRequest request);

    @Operation(summary = "Atualizar um tipo de equipe existente")
    ResponseEntity<TipoEquipeResponse> atualizar(UUID id, TipoEquipeRequest request);

    @Operation(summary = "Buscar um tipo de equipe por ID")
    ResponseEntity<TipoEquipeResponse> buscarPorId(UUID id);

    @Operation(summary = "Listar tipos de equipe com filtros dinâmicos")
    ResponseEntity<PageResponse<TipoEquipeResponse>> listar(@ParameterObject TipoEquipeFilter filter, @ParameterObject Pageable pageable);

    @Operation(summary = "Desativar um tipo de equipe (Soft Delete)")
    ResponseEntity<Void> desativar(UUID id);

    @Operation(summary = "Ativar um tipo de equipe")
    ResponseEntity<Void> ativar(UUID id);

    @Operation(summary = "Validar arquivo CSV para importação de tipos de equipe")
    ResponseEntity<ImportacaoTipoEquipeResponse> importar(MultipartFile arquivo);

    @Operation(summary = "Confirmar a importação dos tipos de equipe validados")
    ResponseEntity<ImportacaoTipoEquipeResponse> confirmarImportacao(ConfirmarImportacaoTipoEquipeRequest request);

    @Operation(summary = "Baixar modelo CSV para importação de tipos de equipe", description = "Disponibiliza o arquivo CSV de exemplo com cabeçalho e formato exatos para importação.")
    ResponseEntity<byte[]> baixarModeloCsv();
}
