package com.nexo.app.modules.sst.doc;

import com.nexo.app.modules.sst.application.dto.ConfirmacaoPerfilNormativoSstRequest;
import com.nexo.app.modules.sst.application.dto.PerfilNormativoGovernancaSstFilter;
import com.nexo.app.modules.sst.application.dto.PerfilNormativoGovernancaSstRequest;
import com.nexo.app.modules.sst.application.dto.PerfilNormativoGovernancaSstResponse;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

@Tag(name = "SST - Governança - Perfis normativos",
        description = "Edições rastreáveis de fontes nacionais, estaduais e configurações internas")
@SecurityRequirement(name = "bearerAuth")
public interface PerfilNormativoGovernancaSstControllerDoc {

    @Operation(summary = "Cadastrar uma edição normativa",
            description = "A edição nasce não confirmada. Alterações futuras devem criar nova versão, preservando o histórico.")
    @ApiResponse(responseCode = "201", description = "Edição cadastrada")
    ResponseEntity<PerfilNormativoGovernancaSstResponse> criar(
            PerfilNormativoGovernancaSstRequest request);

    @Operation(summary = "Confirmar uma edição contra a fonte informada")
    ResponseEntity<PerfilNormativoGovernancaSstResponse> confirmar(
            UUID id, ConfirmacaoPerfilNormativoSstRequest request);

    @Operation(summary = "Buscar uma edição normativa")
    ResponseEntity<PerfilNormativoGovernancaSstResponse> buscarPorId(UUID id);

    @Operation(summary = "Listar edições normativas com filtros")
    ResponseEntity<PageResponse<PerfilNormativoGovernancaSstResponse>> listar(
            @ParameterObject PerfilNormativoGovernancaSstFilter filter, Pageable pageable);
}
