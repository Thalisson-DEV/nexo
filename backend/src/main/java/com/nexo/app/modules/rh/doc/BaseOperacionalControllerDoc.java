package com.nexo.app.modules.rh.doc;

import com.nexo.app.modules.rh.BaseOperacionalResponse;
import com.nexo.app.modules.rh.application.dto.*;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(name = "Bases Operacionais", description = "Gestão de sedes regionais e vínculos financeiros")
@SecurityRequirement(name = "bearerAuth")
public interface BaseOperacionalControllerDoc {

    @Operation(summary = "Registrar nova base operacional")
    ResponseEntity<BaseOperacionalResponse> criar(BaseOperacionalRequest request);

    @Operation(summary = "Atualizar base operacional")
    ResponseEntity<BaseOperacionalResponse> atualizar(UUID id, BaseOperacionalRequest request);

    @Operation(summary = "Desativar base operacional (Soft Delete)")
    ResponseEntity<Void> desativar(UUID id);

    @Operation(summary = "Ativar base operacional")
    ResponseEntity<Void> ativar(UUID id);

    @Operation(summary = "Buscar base operacional por ID")
    ResponseEntity<BaseOperacionalResponse> buscarPorId(UUID id);

    @Operation(summary = "Listar bases operacionais com filtros dinâmicos", description = "Permite filtrar por nome, status e centro de faturamento")
    ResponseEntity<PageResponse<BaseOperacionalResponse>> listar(@ParameterObject BaseOperacionalFilter filter, @ParameterObject Pageable pageable);

    @Operation(summary = "Validar arquivo CSV para importação de bases operacionais")
    ResponseEntity<ImportacaoBaseOperacionalResponse> importar(MultipartFile arquivo);

    @Operation(summary = "Confirmar importação de bases operacionais")
    ResponseEntity<ImportacaoBaseOperacionalResponse> confirmarImportacao(ConfirmarImportacaoBaseRequest request);

    @Operation(summary = "Baixar modelo CSV para importação de bases operacionais", description = "Disponibiliza o arquivo CSV de exemplo com cabeçalho e formato exatos para importação.")
    ResponseEntity<byte[]> baixarModeloCsv();
}
