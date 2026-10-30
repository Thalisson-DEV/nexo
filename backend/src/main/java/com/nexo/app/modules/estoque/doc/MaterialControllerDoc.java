package com.nexo.app.modules.estoque.doc;

import com.nexo.app.modules.estoque.MaterialResponse;
import com.nexo.app.modules.estoque.application.dto.*;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.application.dto.LookupOptionDto;
import com.nexo.app.shared.application.dto.LookupPageDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(name = "Materiais", description = "Gestão do cadastro mestre de materiais")
@SecurityRequirement(name = "bearerAuth")
public interface MaterialControllerDoc {
    @Operation(summary = "Criar novo material")
    ResponseEntity<MaterialResponse> criar(MaterialRequest request);

    @Operation(summary = "Importar via CSV")
    ResponseEntity<ImportacaoMaterialResponse> importar(MultipartFile arquivo);

    @Operation(summary = "Confirmar importação")
    ResponseEntity<ImportacaoMaterialResponse> confirmarImportacao(ConfirmarImportacaoMaterialRequest request);

    @Operation(summary = "Atualizar material")

    ResponseEntity<MaterialResponse> atualizar(UUID id, MaterialRequest request);

    @Operation(summary = "Listar materiais com filtros dinâmicos")
    ResponseEntity<PageResponse<MaterialResponse>> listar(@ParameterObject MaterialFilter filter, @ParameterObject Pageable pageable);

    @Operation(summary = "Buscar opções leves de materiais",
            description = "Busca por prefixo de código ou descrição. Exige no mínimo 2 caracteres; limite máximo 100.")
    ResponseEntity<LookupPageDto<LookupOptionDto>> options(
            @Parameter(description = "Prefixo de código ou descrição; acentos são ignorados",
                    schema = @Schema(minLength = 2)) String q,
            @Parameter(description = "Quantidade por página (padrão 50, máximo 100)",
                    schema = @Schema(defaultValue = "50", maximum = "100", minimum = "1")) Integer limit,
            @Parameter(description = "Cursor opaco da página anterior") String cursor);

    @Operation(summary = "Buscar material por ID")
    ResponseEntity<MaterialResponse> buscarPorId(UUID id);

    @Operation(summary = "Ativar material")
    ResponseEntity<Void> ativar(UUID id);

    @Operation(summary = "Desativar material")
    ResponseEntity<Void> desativar(UUID id);

    @Operation(summary = "Baixar modelo CSV para importação de materiais", description = "Disponibiliza o arquivo CSV de exemplo com cabeçalho e formato exatos para importação.")
    ResponseEntity<byte[]> baixarModeloCsv();
}
