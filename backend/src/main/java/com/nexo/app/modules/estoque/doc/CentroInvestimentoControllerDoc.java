package com.nexo.app.modules.estoque.doc;

import com.nexo.app.modules.estoque.CentroInvestimentoResponse;
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

@Tag(name = "Centros de Investimento", description = "Gestão de centros de investimento (Ex: 670, 610)")
@SecurityRequirement(name = "bearerAuth")
public interface CentroInvestimentoControllerDoc {
    @Operation(summary = "Criar novo centro")
    ResponseEntity<CentroInvestimentoResponse> criar(CentroInvestimentoRequest request);

    @Operation(summary = "Importar via CSV")
    ResponseEntity<ImportacaoCentroInvestimentoResponse> importar(MultipartFile arquivo);

    @Operation(summary = "Confirmar importação")
    ResponseEntity<ImportacaoCentroInvestimentoResponse> confirmarImportacao(ConfirmarImportacaoCentroInvestimentoRequest request);

    @Operation(summary = "Atualizar centro")

    ResponseEntity<CentroInvestimentoResponse> atualizar(UUID id, CentroInvestimentoRequest request);

    @Operation(summary = "Listar com filtros")
    ResponseEntity<PageResponse<CentroInvestimentoResponse>> listar(@ParameterObject CentroInvestimentoFilter filter, @ParameterObject Pageable pageable);

    @Operation(summary = "Buscar por ID")
    ResponseEntity<CentroInvestimentoResponse> buscarPorId(UUID id);

    @Operation(summary = "Ativar")
    ResponseEntity<Void> ativar(UUID id);

    @Operation(summary = "Desativar")
    ResponseEntity<Void> desativar(UUID id);

    @Operation(summary = "Baixar modelo CSV para importação de centros de investimento", description = "Disponibiliza o arquivo CSV de exemplo com cabeçalho e formato exatos para importação.")
    ResponseEntity<byte[]> baixarModeloCsv();
}
