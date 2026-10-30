package com.nexo.app.modules.sst.doc;

import com.nexo.app.modules.sst.application.dto.DesvioDetalhadoResponse;
import com.nexo.app.modules.sst.application.dto.DesvioSegurancaFilter;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

@Tag(name = "SST - Desvios", description = "Listagem geral e detalhe dos desvios de segurança de toda a empresa")
@SecurityRequirement(name = "bearerAuth")
public interface DesvioSegurancaControllerDoc {

    @Operation(
            summary = "Listar todos os desvios de segurança",
            description = """
                    Lista os desvios de toda a empresa — a visão do SESMT — com o contexto completo da inspeção \
                    de origem em cada linha: inspetor, processo, base, equipe, veículo, item do formulário, \
                    colaboradores responsabilizados, anexos e indicador de vencimento.

                    Diferente de `/v1/sst/desvios/meus`, aqui não há recorte pelo usuário autenticado.

                    Status, gravidade, inspetor, colaborador, formulário, pergunta, processo, base operacional, \
                    centro de custo, equipe e veículo aceitam múltipla seleção (parâmetro repetido ou valores \
                    separados por vírgula); o centro de custo equivale a selecionar todas as bases vinculadas a ele.

                    A ordenação segue o `sort` padrão do Spring Data sobre os campos do desvio \
                    (ex.: `sort=prazo,asc` ou `sort=gravidade,desc`); sem `sort`, a listagem devolve os \
                    desvios registrados mais recentemente primeiro.""")
    ResponseEntity<PageResponse<DesvioDetalhadoResponse>> listar(
            @ParameterObject DesvioSegurancaFilter filter, @ParameterObject Pageable pageable);

    @Operation(
            summary = "Detalhar um desvio de segurança",
            description = "Devolve o mesmo item completo da listagem para um único desvio, sem depender de abrir a inspeção de origem.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "Desvio inexistente, desativado ou de uma inspeção desativada"))
    ResponseEntity<DesvioDetalhadoResponse> buscarPorId(UUID idDesvio);
}
