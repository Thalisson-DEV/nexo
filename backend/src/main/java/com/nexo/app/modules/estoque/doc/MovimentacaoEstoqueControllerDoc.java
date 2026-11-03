package com.nexo.app.modules.estoque.doc;

import com.nexo.app.modules.estoque.application.dto.*;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

@Tag(name = "Movimentações de Estoque", description = "Auditoria (Kardex) e ajustes manuais de inventário. " +
        "Garante a trilha de auditoria para cada material movimentado.")
@SecurityRequirement(name = "bearerAuth")
public interface MovimentacaoEstoqueControllerDoc {

    @Operation(summary = "Listar movimentações agrupadas por operação", 
               description = "Retorna uma lista paginada de operações de estoque. Cada operação contém o resumo e o id de referência.")
    ResponseEntity<PageResponse<MovimentacaoOperacaoSummaryResponse>> listar(@ParameterObject MovimentacaoEstoqueFilter filter, @ParameterObject Pageable pageable);

    @Operation(summary = "Buscar detalhamento completo de uma operação (Lote)", 
               description = "Retorna todos os materiais movimentados em uma única operação a partir do ID de referência.")
    ResponseEntity<MovimentacaoOperacaoResponse> buscarOperacaoPorIdReferencia(UUID idReferencia);

    @Operation(summary = "Buscar detalhe de uma movimentação individual por ID", 
               description = "Visualização individual de um registro de entrada, saída ou transferência.")
    ResponseEntity<MovimentacaoEstoqueResponse> buscarPorId(UUID id);

    @Operation(summary = "Registrar entrada avulsa (Ajuste Positivo) em lote", 
               description = "Incrementa o saldo dos materiais no estoque de destino. " +
                             "**Requisitos:** <br>" +
                             "- Apenas 'idEstoqueDestino' e 'itens' são obrigatórios. <br>" +
                             "- Se o material detalha série: 'numSerie' é obrigatório e 'quantidade' deve ser 1.000. <br>" +
                             "- Se não detalha série: 'numSerie' deve ser nulo.")
    ResponseEntity<Void> entradaLote(MovimentacaoBulkRequest request);

    @Operation(summary = "Registrar baixa avulsa (Ajuste Negativo/Perda) em lote", 
               description = "Remove saldo dos materiais no estoque de origem. " +
                             "**Requisitos:** <br>" +
                             "- Apenas 'idEstoqueOrigem' e 'itens' são obrigatórios. <br>" +
                             "- Se o material detalha série: 'numSerie' é obrigatório e 'quantidade' deve ser 1.000. <br>" +
                             "- Se não detalha série: 'numSerie' deve ser nulo.")
    ResponseEntity<Void> baixaLote(MovimentacaoBulkRequest request);

    @Operation(summary = "Registrar transferência manual entre estoques em lote", 
               description = "Movimenta material entre dois estoques. " +
                             "**Requisitos:** <br>" +
                             "- 'idEstoqueOrigem', 'idEstoqueDestino' e 'itens' são obrigatórios. <br>" +
                             "- Se o material detalha série: 'numSerie' é obrigatório e 'quantidade' deve ser 1.000. <br>" +
                             "- Se não detalha série: 'numSerie' deve ser nulo.")
    ResponseEntity<UUID> transferenciaLote(MovimentacaoBulkRequest request);

    @Operation(summary = "Baixar arquivo de assinatura da transferência de estoque", 
               description = "Retorna a imagem PNG da assinatura do entregador ou recebedor de uma operação de transferência.")
    ResponseEntity<org.springframework.core.io.Resource> baixarAssinatura(UUID idReferencia, com.nexo.app.modules.estoque.application.services.AssinaturaTransferenciaService.ParteTransferencia parte);

    @Operation(summary = "Registrar devolução comercial de materiais (Tipo Fixo 222)", 
               description = "Transfere materiais em custódia no estoque de campo da equipe de volta para um almoxarifado físico ativo, nas condições NOVO ou SUCATA.")
    ResponseEntity<UUID> devolucaoComercial(DevolucaoComercialRequest request);

    @Operation(summary = "Registrar devolução técnica de materiais (222 ou 922)",
               description = "Transfere materiais da obra para um almoxarifado físico na hora. Material que não constava no estoque da obra entra nela como entrada avulsa antes de sair.")
    ResponseEntity<UUID> devolucaoTecnica(com.nexo.app.modules.estoque.application.dto.DevolucaoTecnicaRequest request);

    @Operation(summary = "Validar arquivo CSV de importação de movimentações de estoque",
               description = "Analisa e valida o arquivo CSV enviado (entradas, saídas e transferências), verificando cadastros, regras de séries e estoques.")
    ResponseEntity<ImportacaoMovimentacaoResponse> validarImportacao(org.springframework.web.multipart.MultipartFile arquivo);

    @Operation(summary = "Confirmar importação de movimentações de estoque em lote",
               description = "Executa a criação em lote das movimentações validadas (entradas avulsas, baixas avulsas e transferências entre estoques).")
    ResponseEntity<ImportacaoMovimentacaoResponse> confirmarImportacao(ConfirmarImportacaoMovimentacaoRequest request);

    @Operation(summary = "Baixar modelo oficial CSV de importação de movimentações de estoque",
               description = "Retorna a planilha modelo CSV pré-formatada para preenchimento de entradas, saídas e transferências.")
    ResponseEntity<byte[]> baixarModeloCsv();
}
