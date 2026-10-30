package com.nexo.app.modules.lookup.doc;

import com.nexo.app.modules.lookup.application.dto.LookupBootstrapDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Lookups", description = "Contratos leves e estaveis para seletores da interface")
@SecurityRequirement(name = "bearerAuth")
public interface LookupControllerDoc {

    @Operation(
            summary = "Carregar catalogos pequenos dos seletores",
            description = "Retorna somente bases, processos e catalogos fechados do estoque. "
                    + "Nao inclui colaboradores, materiais, obras ou outros datasets volumosos. "
                    + "Suporta If-None-Match e possui contrato v1.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Catalogos carregados",
                    content = @Content(schema = @Schema(implementation = LookupBootstrapDto.class))),
            @ApiResponse(responseCode = "304", description = "Conteudo nao modificado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Permissoes insuficientes", content = @Content)
    })
    ResponseEntity<LookupBootstrapDto> bootstrap(String ifNoneMatch);
}
