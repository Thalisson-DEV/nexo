package com.nexo.app.modules.execucao.doc;

import com.nexo.app.modules.execucao.application.dto.TipoServicoFilter;
import com.nexo.app.modules.execucao.application.dto.TipoServicoRequest;
import com.nexo.app.modules.execucao.application.dto.TipoServicoResponse;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

@Tag(name = "Execução - Tipos de Serviço", description = "Classificação das notas executadas em campo")
@SecurityRequirement(name = "bearerAuth")
public interface TipoServicoControllerDoc {

    @Operation(summary = "Cadastrar tipo de serviço",
            description = "A descrição é única entre os tipos ativos (sem acento e sem diferenciar maiúsculas). O código pode se repetir.")
    @ApiResponse(responseCode = "201", description = "Tipo de serviço cadastrado")
    @ApiResponse(responseCode = "422", description = "Descrição duplicada ou dados inválidos")
    ResponseEntity<TipoServicoResponse> criar(TipoServicoRequest request);

    @Operation(summary = "Buscar tipo de serviço por ID")
    ResponseEntity<TipoServicoResponse> buscarPorId(UUID id);

    @Operation(summary = "Listar tipos de serviço com filtros e paginação")
    ResponseEntity<PageResponse<TipoServicoResponse>> listar(
            @ParameterObject TipoServicoFilter filter, @ParameterObject Pageable pageable);

    @Operation(summary = "Listar tipos de serviço ativos",
            description = "Sem paginação, em ordem alfabética — abastece o seletor do lançamento de execução no app de campo.")
    ResponseEntity<List<TipoServicoResponse>> listarOpcoes();

    @Operation(summary = "Atualizar tipo de serviço")
    @ApiResponse(responseCode = "422", description = "Descrição duplicada ou tipo inativo")
    ResponseEntity<TipoServicoResponse> atualizar(UUID id, TipoServicoRequest request);

    @Operation(summary = "Alternar status ativo/inativo do tipo de serviço",
            description = "Um tipo desativado sai do app de campo; as execuções já lançadas com ele continuam com o tipo.")
    ResponseEntity<Void> alternarStatus(UUID id);
}
