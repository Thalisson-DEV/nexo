package com.nexo.app.modules.sst.doc;

import com.nexo.app.modules.sst.application.dto.AtivacaoCorpoSstRequest;
import com.nexo.app.modules.sst.application.dto.CorpoGovernancaSstDetalheResponse;
import com.nexo.app.modules.sst.application.dto.CorpoGovernancaSstFilter;
import com.nexo.app.modules.sst.application.dto.CorpoGovernancaSstRequest;
import com.nexo.app.modules.sst.application.dto.CorpoGovernancaSstResponse;
import com.nexo.app.modules.sst.application.dto.CorpoGovernancaSstUpdateRequest;
import com.nexo.app.modules.sst.application.dto.EncerramentoCorpoSstRequest;
import com.nexo.app.modules.sst.application.dto.InclusaoMembroCorpoSstRequest;
import com.nexo.app.modules.sst.application.dto.MembroCorpoSstResponse;
import com.nexo.app.modules.sst.application.dto.SaidaMembroCorpoSstRequest;
import com.nexo.app.modules.sst.application.dto.SubstituicaoMembroCorpoSstRequest;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

@Tag(name = "SST - Governança - Corpos",
        description = "Ciclos, composições e histórico de CIPA e Brigada")
@SecurityRequirement(name = "bearerAuth")
public interface CorpoGovernancaSstControllerDoc {

    @Operation(summary = "Criar um ciclo em preparação")
    @ApiResponse(responseCode = "201", description = "Ciclo criado")
    ResponseEntity<CorpoGovernancaSstDetalheResponse> criar(
            @Parameter(description = "Chave idempotente do comando") String idempotencyKey,
            CorpoGovernancaSstRequest request);

    @Operation(summary = "Buscar corpo e composição histórica")
    ResponseEntity<CorpoGovernancaSstDetalheResponse> buscarPorId(UUID id);

    @Operation(summary = "Listar corpos com filtros combináveis",
            description = "O filtro de base organiza resultados; não substitui autorização por carteira de bases.")
    ResponseEntity<PageResponse<CorpoGovernancaSstResponse>> listar(
            @ParameterObject CorpoGovernancaSstFilter filter, Pageable pageable);

    @Operation(summary = "Atualizar a identificação de um ciclo em preparação")
    ResponseEntity<CorpoGovernancaSstDetalheResponse> atualizar(
            UUID id, CorpoGovernancaSstUpdateRequest request);

    @Operation(summary = "Incluir integrante com snapshot validado no RH")
    ResponseEntity<MembroCorpoSstResponse> adicionarMembro(
            UUID idCorpo, InclusaoMembroCorpoSstRequest request);

    @Operation(summary = "Registrar saída motivada sem excluir o histórico")
    ResponseEntity<MembroCorpoSstResponse> registrarSaida(
            String idempotencyKey, UUID idCorpo, UUID idMembro,
            SaidaMembroCorpoSstRequest request);

    @Operation(summary = "Substituir integrante em uma única transação")
    ResponseEntity<MembroCorpoSstResponse> substituir(
            String idempotencyKey, UUID idCorpo, UUID idMembro,
            SubstituicaoMembroCorpoSstRequest request);

    @Operation(summary = "Ativar a composição",
            description = "Exige aprovação e é protegida por unicidade persistente sob concorrência.")
    @ApiResponse(responseCode = "409", description = "Outro ciclo já está ativo na unidade ou a versão está obsoleta")
    ResponseEntity<CorpoGovernancaSstDetalheResponse> ativar(
            String idempotencyKey, UUID id, AtivacaoCorpoSstRequest request);

    @Operation(summary = "Encerrar um ciclo",
            description = "CIPA antecipada somente por encerramento do estabelecimento com evidência administrativa.")
    ResponseEntity<CorpoGovernancaSstDetalheResponse> encerrar(
            String idempotencyKey, UUID id, EncerramentoCorpoSstRequest request);
}
