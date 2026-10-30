package com.nexo.app.modules.estoque.doc;

import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.nexo.app.modules.estoque.application.dto.AprovarSolicitacaoRequest;
import com.nexo.app.modules.estoque.application.dto.ConsumoAnteriorDetalheResponse;
import com.nexo.app.modules.estoque.application.dto.ConsumoAnteriorSolicitacaoResponse;
import com.nexo.app.modules.estoque.application.dto.DefinirSeriesReservadasRequest;
import com.nexo.app.modules.estoque.application.dto.ConfirmarImportacaoSolicitacaoComercialRequest;
import com.nexo.app.modules.estoque.application.dto.EntregaSolicitacaoRequest;
import com.nexo.app.modules.estoque.application.dto.EstornoParcialRequest;
import com.nexo.app.modules.estoque.application.dto.ImportacaoSolicitacaoComercialResponse;
import com.nexo.app.modules.estoque.application.dto.SolicitacaoFilter;
import com.nexo.app.modules.estoque.application.dto.SolicitacaoRequest;
import com.nexo.app.modules.estoque.application.dto.SolicitacaoResponse;
import com.nexo.app.modules.estoque.application.dto.SolicitacaoSummaryResponse;
import com.nexo.app.modules.estoque.application.services.AssinaturaSolicitacaoMaterialService;
import com.nexo.app.shared.application.dto.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Solicitações de Material", description = "Gestão de pedidos e logística de materiais. " +
        "Fluxo: PENDENTE -> (APROVADO/REJEITADO) -> ENTREGUE (PARCIALMENTE OU COMPLETAMENTE) -> ESTORNADO.")
@SecurityRequirement(name = "bearerAuth")
public interface SolicitacaoControllerDoc {

    @Operation(summary = "Criar nova solicitação (Fluxo Equipe)", 
               description = "O 'idRequisitante' é extraído automaticamente do Token JWT do usuário logado. " +
                             "A solicitação nasce com status PENDENTE e não valida saldo nesta etapa.")
    ResponseEntity<UUID> criar(SolicitacaoRequest request);

    @Operation(summary = "Criar solicitação validada (Fluxo Backoffice)", 
               description = "O autor e aprovador são obtidos do usuário logado. " +
                             "A equipe e o colaborador previstos para o saque são informados no corpo. " +
                             "O status já nasce como APROVADO.")
    ResponseEntity<UUID> criarDireta(SolicitacaoRequest request);

    @Operation(summary = "Listar solicitações com filtros", 
               description = "Retorna uma lista resumida das solicitações (HATEOAS). " +
                             "Utilize o link 'self' de cada item para obter o detalhamento completo dos materiais. " +
                             "Para filas técnicas, use etapa=AGUARDANDO_PLANEJAMENTO, EM_SEPARACAO ou " +
                             "PENDENTE_ENTREGA; o filtro é aplicado antes da paginação.")
    ResponseEntity<PageResponse<SolicitacaoSummaryResponse>> listar(@ParameterObject SolicitacaoFilter filter, @ParameterObject Pageable pageable);

    @Operation(summary = "Buscar solicitação por ID", 
               description = "Retorna o DTO completo, incluindo a lista de itens e o histórico de datas.")
    ResponseEntity<SolicitacaoResponse> buscarPorId(UUID id);

    @Operation(summary = "Consumo desde a última solicitação comercial",
               description = "Para cada material: quanto o estoque de destino recebeu na última solicitação comercial " +
                             "daquele material, quanto aplicou em campo e devolveu desde aquela entrega, e o saldo NOVO atual. " +
                             "A janela termina na primeira entrega desta solicitação (ou agora, se ainda não foi entregue).")
    ResponseEntity<ConsumoAnteriorSolicitacaoResponse> consumoAnterior(UUID id);

    @Operation(summary = "Definir as séries reservadas de um item",
               description = "O almoxarifado define antes da entrega quais séries novas da origem vão para o item; " +
                             "na entrega, só confirma. A lista substitui a anterior (vazia libera tudo). Enquanto a " +
                             "solicitação aceita entrega e o item tem saldo, a série não sai da origem por outro caminho.")
    ResponseEntity<java.util.List<String>> definirSeriesReservadas(UUID id, UUID idItem, DefinirSeriesReservadasRequest request);

    @Operation(summary = "Detalhar o consumo de um material desde a última solicitação",
               description = "As movimentações do estoque de destino que explicam o consumo do material: entregas, " +
                             "aplicações (com a nota), devoluções, transferências, baixas e retiradas no mesmo período do " +
                             "resumo, com sinal (+ entrou, − saiu), e o saldo NOVO no início e no fim do período.")
    ResponseEntity<ConsumoAnteriorDetalheResponse> consumoAnteriorDoMaterial(UUID id, UUID idMaterial);

    @Operation(summary = "Exibir assinatura digital da solicitacao",
               description = "Retorna a imagem protegida da assinatura mais recente. Assinaturas fisicas retornam 204.")
    ResponseEntity<Resource> baixarAssinatura(
            UUID id, AssinaturaSolicitacaoMaterialService.Parte parte);

    @Operation(summary = "Aprovar solicitação", 
               description = "Altera status para APROVADO. Apenas solicitações PENDENTES podem ser aprovadas. " +
                             "Permite editar as quantidades solicitadas dos itens antes de aprovar. " +
                             "O 'idAprovador' é registrado automaticamente via Token JWT.")
    @ApiResponse(responseCode = "204", description = "Solicitação aprovada com sucesso")
    ResponseEntity<Void> aprovar(UUID id, @RequestBody(required = false) AprovarSolicitacaoRequest request);

    @Operation(summary = "Rejeitar solicitação",
               description = "Altera status para REJEITADO. Requer um motivo de rejeição e uma justificativa. Encerra o ciclo de vida da solicitação.")
    @ApiResponse(responseCode = "204", description = "Solicitação rejeitada com sucesso")
    ResponseEntity<Void> rejeitar(UUID id, @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Dados do motivo de rejeição") com.nexo.app.modules.estoque.application.dto.RejeitarSolicitacaoRequest request);
    @Operation(summary = "Registrar entrega física de material", 
               description = "ESTA OPERAÇÃO MOVIMENTA SALDOS. Retira do Estoque Origem e adiciona no Estoque Destino. " +
                             "Gera histórico de movimentação (Kardex). <br>" +
                             "**Regra de Série:** <br>" +
                             "- Se o material detalha série: 'numSerie' é obrigatório no item e a 'quantidade' deve ser 1.000. <br>" +
                             "- Se não detalha série: 'numSerie' deve ser nulo.")
    @ApiResponse(responseCode = "200", description = "Entrega registrada e saldos atualizados. Retorna o ID da operação de movimentação.")
    ResponseEntity<UUID> registrarEntrega(UUID id, @RequestBody EntregaSolicitacaoRequest request);

    @Operation(summary = "Estornar solicitação já entregue", 
               description = "REVERTE OS SALDOS TOTAIS. Só pode ser feito se o status for ENTREGUE ou ENTREGUE_PARCIAL. " +
                             "Devolve o material ao Estoque de Origem e retira do Estoque de Destino.")
    @ApiResponse(responseCode = "200", description = "Saldos revertidos e status alterado para ESTORNADO. Retorna o ID da operação de movimentação.")
    ResponseEntity<UUID> estornar(UUID id);

    @Operation(summary = "Estornar parcialmente itens entregues", 
               description = "REVERTE SALDOS DOS ITENS INFORMADOS. <br>" +
                             "Se o estorno zerar todas as entregas, o status volta para APROVADO. <br>" +
                             "Caso contrário, o status é ajustado entre ENTREGUE ou ENTREGUE_PARCIAL conforme a situação dos itens.")
    @ApiResponse(responseCode = "200", description = "Saldos dos itens informados revertidos com sucesso. Retorna o ID da operação de movimentação.")
    ResponseEntity<UUID> estornarParcial(UUID id, @RequestBody EstornoParcialRequest request);

    @Operation(summary = "Exportar solicitações de material em formato CSV ou ZIP (Resumo)",
               description = "Gera arquivo tabular com 1 linha por solicitação de material. Suporta layouts dedicados por processo, visão canônica consolidada e pacote ZIP com arquivos separados.")
    @ApiResponse(responseCode = "200", description = "Arquivo CSV ou ZIP em formato streaming")
    ResponseEntity<StreamingResponseBody> exportarResumo(
            @ParameterObject SolicitacaoFilter filter,
            @Parameter(description = "Se verdadeiro, restringe às solicitações do usuário logado") Boolean apenasMinhas,
            @Parameter(description = "Se verdadeiro e contexto for TODOS, gera arquivo compactado ZIP com os CSVs comercial e técnico separados") Boolean pacoteZip
    );

    @Operation(summary = "Exportar solicitações de material em formato CSV ou ZIP (Completo / Detalhado)",
               description = "Gera arquivo tabular com 1 linha por item de material. Suporta layouts dedicados por processo, visão canônica consolidada e pacote ZIP com arquivos separados.")
    @ApiResponse(responseCode = "200", description = "Arquivo CSV ou ZIP em formato streaming")
    ResponseEntity<StreamingResponseBody> exportarDetalhado(
            @ParameterObject SolicitacaoFilter filter,
            @Parameter(description = "Se verdadeiro, restringe às solicitações do usuário logado") Boolean apenasMinhas,
            @Parameter(description = "Se verdadeiro e contexto for TODOS, gera arquivo compactado ZIP com os CSVs comercial e técnico separados") Boolean pacoteZip
    );

    @Operation(summary = "Validar arquivo de importação de solicitações comerciais",
               description = "Etapa síncrona: analisa CSV UTF-8 e retorna prévia, processamento e erros sem persistir.")
    @ApiResponse(responseCode = "200", description = "Prévia da importação com contagens e itens validados")
    ResponseEntity<ImportacaoSolicitacaoComercialResponse> validarImportacaoComercial(
            @Parameter(description = "Arquivo CSV (.csv) preenchido") org.springframework.web.multipart.MultipartFile arquivo);

    @Operation(summary = "Confirmar importação de solicitações comerciais",
               description = "Etapa síncrona: revalida os registros, cria as solicitações elegíveis e devolve estado final, contagens e erros por linha.")
    @ApiResponse(responseCode = "200", description = "Resultado da confirmação com contagens")
    ResponseEntity<ImportacaoSolicitacaoComercialResponse> confirmarImportacaoComercial(
            @RequestBody @jakarta.validation.Valid ConfirmarImportacaoSolicitacaoComercialRequest request);

    @Operation(summary = "Baixar modelo CSV para importação de solicitações comerciais",
               description = "Retorna arquivo modelo no formato CSV UTF-8 com instruções e cabeçalhos.")
    ResponseEntity<byte[]> baixarModeloComercialCsv();

    @Operation(summary = "Obter quadro Kanban de solicitações comerciais",
               description = "Retorna as 4 macro-colunas operacionais com até 30 cards cada e métricas agregadas para solicitações comerciais.")
    ResponseEntity<com.nexo.app.modules.estoque.application.dto.KanbanSolicitacaoTecnicaResponse> obterKanban(
            @ParameterObject SolicitacaoFilter filter);

    @Operation(summary = "Iniciar separação de solicitação comercial",
               description = "Avança solicitação comercial aprovada para EM_SEPARACAO.")
    ResponseEntity<com.nexo.app.modules.estoque.domain.StatusSeparacao> iniciarSeparacao(
            UUID id,
            @jakarta.validation.Valid com.nexo.app.modules.estoque.application.dto.AlterarSeparacaoTecnicaRequest request,
            String chaveIdempotencia);

    @Operation(summary = "Concluir separação de solicitação comercial",
               description = "Avança solicitação comercial em separação para SEPARADO.")
    ResponseEntity<com.nexo.app.modules.estoque.domain.StatusSeparacao> concluirSeparacao(
            UUID id,
            @jakarta.validation.Valid com.nexo.app.modules.estoque.application.dto.AlterarSeparacaoTecnicaRequest request,
            String chaveIdempotencia);

    @Operation(summary = "Pular separação de solicitação comercial",
               description = "Pula a separação física avançando direto para SEPARADO.")
    ResponseEntity<com.nexo.app.modules.estoque.domain.StatusSeparacao> pularSeparacao(
            UUID id,
            @jakarta.validation.Valid com.nexo.app.modules.estoque.application.dto.AlterarSeparacaoTecnicaRequest request,
            String chaveIdempotencia);
}
