package com.nexo.app.modules.obras.doc;

import com.nexo.app.modules.obras.application.dto.CarteiraImportacaoLinha;
import com.nexo.app.modules.obras.application.dto.ConfirmarImportacaoPlanilhaRequest;
import com.nexo.app.modules.obras.application.dto.HistoricoImportacaoResponse;
import com.nexo.app.modules.obras.application.dto.OrcamentoImportacaoLinha;
import com.nexo.app.modules.obras.application.dto.PreviaImportacaoObraResponse;
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

    @Operation(summary = "Validar a planilha da carteira e devolver a pré-visualização por linha",
            description = "Obra existente (pelo número) recebe só as colunas de carteira preenchidas; obra nova exige os dados do cadastro. Nada é gravado.")
    ResponseEntity<PreviaImportacaoObraResponse<CarteiraImportacaoLinha>> validarCarteira(
            @Parameter(description = "Contrato da carteira") UUID idContrato, MultipartFile arquivo);

    @Operation(summary = "Revalidar as linhas da pré-visualização da carteira depois de uma correção (nada é gravado)")
    ResponseEntity<PreviaImportacaoObraResponse<CarteiraImportacaoLinha>> revalidarCarteira(
            ConfirmarImportacaoPlanilhaRequest<CarteiraImportacaoLinha> request);

    @Operation(summary = "Confirmar a importação da carteira",
            description = "Revalida as linhas; as válidas são processadas em segundo plano. Planejamento, programação e medições não são alterados.")
    ResponseEntity<PreviaImportacaoObraResponse<CarteiraImportacaoLinha>> confirmarCarteira(
            ConfirmarImportacaoPlanilhaRequest<CarteiraImportacaoLinha> request);

    @Operation(summary = "Baixar o modelo XLSX da importação da carteira")
    ResponseEntity<byte[]> baixarModeloCarteira();

    @Operation(summary = "Validar a planilha de serviços/orçamento previstos e devolver a pré-visualização por linha")
    ResponseEntity<PreviaImportacaoObraResponse<OrcamentoImportacaoLinha>> validarOrcamento(MultipartFile arquivo);

    @Operation(summary = "Revalidar as linhas da pré-visualização do orçamento depois de uma correção (nada é gravado)")
    ResponseEntity<PreviaImportacaoObraResponse<OrcamentoImportacaoLinha>> revalidarOrcamento(
            ConfirmarImportacaoPlanilhaRequest<OrcamentoImportacaoLinha> request);

    @Operation(summary = "Confirmar a importação de serviços/orçamento previstos",
            description = "Para cada obra da planilha, os serviços previstos anteriores são substituídos pelos novos.")
    ResponseEntity<PreviaImportacaoObraResponse<OrcamentoImportacaoLinha>> confirmarOrcamento(
            ConfirmarImportacaoPlanilhaRequest<OrcamentoImportacaoLinha> request);

    @Operation(summary = "Baixar o modelo XLSX de serviços/orçamento previstos")
    ResponseEntity<byte[]> baixarModeloOrcamento();
}
