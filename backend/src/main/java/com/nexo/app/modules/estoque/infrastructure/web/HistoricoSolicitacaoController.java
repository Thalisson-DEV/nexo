package com.nexo.app.modules.estoque.infrastructure.web;

import com.nexo.app.modules.estoque.application.dto.HistoricoSolicitacaoFilter;
import com.nexo.app.modules.estoque.application.dto.HistoricoSolicitacaoResponse;
import com.nexo.app.modules.estoque.application.usecases.HistoricoSolicitacaoUseCases;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Linha do tempo auditável de qualquer solicitação, comercial ou técnica. Fica fora do
 * controller técnico porque ele só sobe com o fluxo técnico ligado, e o comercial também usa.
 */
@RestController
@RequestMapping("/v1/solicitacoes-material")
@RequiredArgsConstructor
@Tag(name = "Historico de solicitacoes de material", description = "Cadeia auditavel de eventos e diffs de cada solicitacao.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Preenchimento ou referencia invalida",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(
                        implementation = com.nexo.app.shared.application.dto.ProblemaHttpResponse.class))),
        @ApiResponse(responseCode = "403", description = "Autoridade ausente",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(
                        implementation = com.nexo.app.shared.application.dto.ProblemaHttpResponse.class))),
        @ApiResponse(responseCode = "404", description = "Recurso nao encontrado",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(
                        implementation = com.nexo.app.shared.application.dto.ProblemaHttpResponse.class))),
        @ApiResponse(responseCode = "409", description = "Conflito de versao, estado ou idempotencia",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(
                        implementation = com.nexo.app.shared.application.dto.ProblemaHttpResponse.class))),
        @ApiResponse(responseCode = "422", description = "Regra de negocio ou preenchimento de dominio invalido",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(
                        implementation = com.nexo.app.shared.application.dto.ProblemaHttpResponse.class)))
})
public class HistoricoSolicitacaoController {

    private final HistoricoSolicitacaoUseCases historico;

    @GetMapping("/{idSolicitacao}/historico")
    @Operation(summary = "Consultar cadeia auditavel do filho",
            description = "Retorna integridade HMAC, eventos e diffs paginados; aceita filtros, page, size e sort. "
                    + "Autoridade: estoque:solicitacao:audit:read.")
    @ApiResponse(responseCode = "200", description = "Pagina de eventos e integridade da cadeia")
    @PreAuthorize("hasAuthority('estoque:solicitacao:audit:read')")
    public ResponseEntity<HistoricoSolicitacaoResponse> historicoPorSolicitacao(
            @PathVariable UUID idSolicitacao,
            @ParameterObject HistoricoSolicitacaoFilter filter,
            @ParameterObject Pageable pageable
    ) {
        return ResponseEntity.ok(historico.porSolicitacao(idSolicitacao, filter, pageable));
    }
}
