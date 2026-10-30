package com.nexo.app.modules.estoque.doc;

import com.nexo.app.modules.estoque.AvaliacaoMaterialResponse;
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

@Tag(name = "Avaliações de Material", description = "Gestão de avaliações técnicas (Ex: INVEST, MANUT)")
@SecurityRequirement(name = "bearerAuth")
public interface AvaliacaoMaterialControllerDoc {
    @Operation(summary = "Criar nova avaliação")
    ResponseEntity<AvaliacaoMaterialResponse> criar(AvaliacaoMaterialRequest request);

    @Operation(summary = "Importar via CSV")
    ResponseEntity<ImportacaoAvaliacaoMaterialResponse> importar(MultipartFile arquivo);

    @Operation(summary = "Confirmar importação")
    ResponseEntity<ImportacaoAvaliacaoMaterialResponse> confirmarImportacao(ConfirmarImportacaoAvaliacaoMaterialRequest request);

    @Operation(summary = "Atualizar avaliação")
    ResponseEntity<AvaliacaoMaterialResponse> atualizar(UUID id, AvaliacaoMaterialRequest request);

    @Operation(summary = "Listar com filtros")
    ResponseEntity<PageResponse<AvaliacaoMaterialResponse>> listar(@ParameterObject AvaliacaoMaterialFilter filter, @ParameterObject Pageable pageable);

    @Operation(summary = "Buscar por ID")
    ResponseEntity<AvaliacaoMaterialResponse> buscarPorId(UUID id);

    @Operation(summary = "Ativar")
    ResponseEntity<Void> ativar(UUID id);

    @Operation(summary = "Desativar")
    ResponseEntity<Void> desativar(UUID id);

    @Operation(summary = "Baixar modelo CSV para importação de avaliações de material", description = "Disponibiliza o arquivo CSV de exemplo com cabeçalho e formato exatos para importação.")
    ResponseEntity<byte[]> baixarModeloCsv();
}
