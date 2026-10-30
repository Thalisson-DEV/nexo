package com.nexo.app.modules.sst.doc;

import com.nexo.app.modules.sst.application.dto.*;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

@Tag(name = "SST - Meus Desvios", description = "Caixa pessoal para consulta e tratamento rápido de desvios")
@SecurityRequirement(name = "bearerAuth")
public interface MeuDesvioControllerDoc {

    @Operation(
            summary = "Listar os desvios das inspeções do usuário",
            description = "Retorna somente desvios identificados em inspeções lançadas pelo inspetor autenticado, com filtros, contexto da inspeção e indicador de vencimento. "
                    + "Status, gravidade, processo, equipe, base operacional e centro de custo aceitam múltipla seleção "
                    + "(parâmetro repetido ou valores separados por vírgula); o centro de custo equivale a selecionar todas as bases vinculadas a ele.")
    ResponseEntity<PageResponse<MeuDesvioResponse>> listar(
            @ParameterObject MeuDesvioFilter filter, @ParameterObject Pageable pageable);

    @Operation(summary = "Tratar um desvio de uma inspeção do usuário")
    ResponseEntity<MeuDesvioResponse> tratar(UUID idDesvio, TratarDesvioRequest request);

    @Operation(
            summary = "Tratar desvios em massa",
            description = "Aplica o mesmo tratamento a até 100 desvios. A operação é atômica e aceita somente desvios de inspeções lançadas pelo inspetor autenticado.")
    ResponseEntity<TratamentoDesviosEmMassaResponse> tratarEmMassa(TratarDesviosEmMassaRequest request);
}
