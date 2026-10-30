package com.nexo.app.modules.estoque.doc;

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
import java.util.List;

@Tag(name = "Estoques", description = "Gestão de estoques físicos e de campo")
@SecurityRequirement(name = "bearerAuth")
public interface EstoqueControllerDoc {
    @Operation(summary = "Criar novo estoque")
    ResponseEntity<EstoqueResponse> criar(EstoqueRequest request);

    @Operation(summary = "Importar via CSV")
    ResponseEntity<ImportacaoEstoqueResponse> importar(MultipartFile arquivo);

    @Operation(summary = "Confirmar importação")
    ResponseEntity<ImportacaoEstoqueResponse> confirmarImportacao(ConfirmarImportacaoEstoqueRequest request);

    @Operation(summary = "Atualizar estoque")

    ResponseEntity<EstoqueResponse> atualizar(UUID id, EstoqueRequest request);

    @Operation(summary = "Listar estoques com filtros dinâmicos")
    ResponseEntity<PageResponse<EstoqueResponse>> listar(@ParameterObject EstoqueFilter filter, @ParameterObject Pageable pageable);

    @Operation(summary = "Buscar opcoes leves de estoques",
            description = "Busca por prefixo, somente em estoques ativos e nos tipos informados. Exige no minimo 2 caracteres.")
    ResponseEntity<LookupPageDto<LookupOptionDto>> options(
            @Parameter(description = "Prefixo do nome; acentos sao ignorados",
                    schema = @Schema(minLength = 2)) String q,
            @Parameter(description = "Quantidade por pagina",
                    schema = @Schema(defaultValue = "50", maximum = "100", minimum = "1")) Integer limit,
            @Parameter(description = "Cursor opaco da pagina anterior") String cursor,
            @Parameter(description = "Tipos permitidos, por exemplo OBRA,FISICO") List<String> tipos);

    @Operation(summary = "Buscar estoque por ID")
    ResponseEntity<EstoqueResponse> buscarPorId(UUID id);

    @Operation(summary = "Ativar")
    ResponseEntity<Void> ativar(UUID id);

    @Operation(summary = "Desativar")
    ResponseEntity<Void> desativar(UUID id);

    @Operation(summary = "Baixar modelo CSV para importação de estoques", description = "Disponibiliza o arquivo CSV de exemplo com cabeçalho e formato exatos para importação.")
    ResponseEntity<byte[]> baixarModeloCsv();
}
