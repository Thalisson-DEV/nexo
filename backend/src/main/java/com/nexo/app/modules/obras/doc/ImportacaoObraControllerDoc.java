package com.nexo.app.modules.obras.doc;

import com.nexo.app.modules.obras.application.dto.HistoricoImportacaoResponse;
import com.nexo.app.modules.obras.domain.ImportacaoObra;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(name = "Obras - Importações", description = "Histórico e andamento das importações de planilha de Obras")
@SecurityRequirement(name = "bearerAuth")
public interface ImportacaoObraControllerDoc {

    @Operation(summary = "Listar o histórico de importações de um tipo (KITS, CARTEIRA, ORCAMENTO), mais recentes primeiro")
    ResponseEntity<PageResponse<HistoricoImportacaoResponse>> listar(ImportacaoObra.Tipo tipo, @ParameterObject Pageable pageable);

    @Operation(summary = "Consultar uma importação com o relatório de erros por linha")
    ResponseEntity<HistoricoImportacaoResponse> buscarPorId(UUID id);

    @Operation(summary = "Importar a carteira de obras do contrato (assíncrono)",
            description = "Cria obras novas e atualiza as existentes pelo número; célula vazia mantém o valor atual. "
                    + "Planejamento, programação e medições não são alterados. Acompanhe pelo histórico.")
    ResponseEntity<HistoricoImportacaoResponse> importarCarteira(
            @Parameter(description = "Contrato da carteira") UUID idContrato, MultipartFile arquivo);

    @Operation(summary = "Baixar o modelo XLSX da importação da carteira")
    ResponseEntity<byte[]> baixarModeloCarteira();

    @Operation(summary = "Importar serviços e orçamento previstos por obra (assíncrono)",
            description = "Para cada obra da planilha, os serviços previstos anteriores são substituídos pelos novos.")
    ResponseEntity<HistoricoImportacaoResponse> importarOrcamento(MultipartFile arquivo);

    @Operation(summary = "Baixar o modelo XLSX da importação de serviços/orçamento previstos")
    ResponseEntity<byte[]> baixarModeloOrcamento();
}
