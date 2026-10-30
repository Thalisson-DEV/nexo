package com.nexo.app.modules.financeiro.doc;

import com.nexo.app.modules.financeiro.application.dto.AprovacaoRequest;
import com.nexo.app.modules.financeiro.application.dto.EventoSolicitacaoResponse;
import com.nexo.app.modules.financeiro.application.dto.MotivoRequest;
import com.nexo.app.modules.financeiro.application.dto.PrePreenchimentoResponse;
import com.nexo.app.modules.financeiro.application.dto.SolicitacaoPagamentoFilter;
import com.nexo.app.modules.financeiro.application.dto.SolicitacaoPagamentoResponse;
import com.nexo.app.modules.financeiro.application.dto.SolicitacaoRequest;
import com.nexo.app.modules.financeiro.application.dto.DocumentoResumoResponse;
import com.nexo.app.modules.financeiro.domain.TipoDocumentoFinanceiro;
import com.nexo.app.modules.iam.domain.Usuario;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.UUID;

/**
 * Solicitações de pagamento: preenchimento, vínculo opcional com uma compra
 * aprovada, decisão e anexos.
 */
@Tag(name = "Financeiro — Solicitações de Pagamento",
        description = "Pedidos de autorização de pagamento. Podem nascer de uma compra aprovada — herdando os "
                + "dados como sugestão — ou existir por conta própria, para despesas que não passam por compra.")
public interface SolicitacaoPagamentoDoc {

    @Operation(summary = "Abrir solicitação de pagamento (rascunho)")
    @ApiResponse(responseCode = "201", description = "Rascunho criado")
    @ApiResponse(responseCode = "422", description = "Preenchimento inválido — veja a propriedade `erros`")
    ResponseEntity<SolicitacaoPagamentoResponse> criar(@RequestBody SolicitacaoRequest request,
                                                       @Parameter(hidden = true) Usuario usuario);

    @Operation(summary = "Atualizar o preenchimento",
            description = "Substitui o conteúdo. Só é permitido enquanto a solicitação for rascunho.")
    @ApiResponse(responseCode = "200", description = "Solicitação atualizada")
    ResponseEntity<SolicitacaoPagamentoResponse> atualizar(@PathVariable UUID id,
                                                           @RequestBody SolicitacaoRequest request);

    @Operation(summary = "Submeter para aprovação")
    @ApiResponse(responseCode = "200", description = "Solicitação submetida")
    ResponseEntity<SolicitacaoPagamentoResponse> submeter(@PathVariable UUID id);

    @Operation(summary = "Pré-preencher a partir de uma compra aprovada",
            description = "Devolve os valores herdáveis já filtrados pelos campos que a variante possui, com o "
                    + "valor autorizado na compra como sugestão. Tudo continua editável: o que se paga pode "
                    + "diferir legitimamente do que se autorizou comprar.")
    @ApiResponse(responseCode = "200", description = "Pré-preenchimento retornado")
    @ApiResponse(responseCode = "422", description = "Compra ainda não aprovada")
    ResponseEntity<PrePreenchimentoResponse> prePreencher(@RequestParam UUID compraId,
                                                          @RequestParam UUID formularioId);

    @Operation(summary = "Aprovar solicitação",
            description = "Aceita um `valorAprovado` diferente do solicitado, para autorizar parcialmente.")
    @ApiResponse(responseCode = "200", description = "Solicitação aprovada")
    ResponseEntity<SolicitacaoPagamentoResponse> aprovar(@PathVariable UUID id,
                                                         @RequestBody AprovacaoRequest request,
                                                         @Parameter(hidden = true) Usuario usuario);

    @Operation(summary = "Rejeitar solicitação", description = "Exige justificativa.")
    @ApiResponse(responseCode = "200", description = "Solicitação rejeitada")
    ResponseEntity<SolicitacaoPagamentoResponse> rejeitar(@PathVariable UUID id,
                                                          @RequestBody MotivoRequest request,
                                                          @Parameter(hidden = true) Usuario usuario);

    @Operation(summary = "Desfazer decisão",
            description = "Devolve uma solicitação aprovada ou rejeitada à fila de aprovação. Exige motivo, "
                    + "registra a decisão desfeita no histórico e avisa quem pediu e quem tinha decidido.")
    @ApiResponse(responseCode = "200", description = "Solicitação de volta a aguardar aprovação")
    @ApiResponse(responseCode = "422", description = "Solicitação sem decisão a desfazer")
    ResponseEntity<SolicitacaoPagamentoResponse> desfazerDecisao(@PathVariable UUID id, @RequestBody MotivoRequest request,
                                                              @Parameter(hidden = true) Usuario usuario);

    @Operation(summary = "Histórico da solicitação",
            description = "Transições em ordem cronológica: abertura, envio, decisões, decisões desfeitas e cancelamento.")
    ResponseEntity<List<EventoSolicitacaoResponse>> historico(@PathVariable UUID id);

    @Operation(summary = "Cancelar solicitação")
    @ApiResponse(responseCode = "200", description = "Solicitação cancelada")
    ResponseEntity<SolicitacaoPagamentoResponse> cancelar(@PathVariable UUID id,
                                                          @RequestBody MotivoRequest request,
                                                          @Parameter(hidden = true) Usuario usuario);

    @Operation(summary = "Anexar um ou mais documentos",
            description = "Recebe uma ou mais partes multipart chamadas `arquivo`; enviar apenas uma "
                    + "continua suportado. Aceita imagem, PDF, XML, Word e Excel, até 15 MB por arquivo. "
                    + "Use `tipo=NOTA_FISCAL` ou "
                    + "`tipo=BOLETO` para que a contabilidade localize o documento sem abrir todos. "
                    + "O documento entra no repositório financeiro já vinculado a este pagamento e "
                    + "herda dele fornecedor, valor, vencimento, base, centro de custo e obra — no caso "
                    + "da nota fiscal, também o número já digitado no formulário. "
                    + "**É permitido depois da aprovação**, que é quando a nota e o boleto chegam.")
    @ApiResponse(responseCode = "201", description = "Documento(s) adicionado(s)")
    ResponseEntity<SolicitacaoPagamentoResponse> adicionarDocumento(@PathVariable UUID id,
                                                                    @RequestPart("arquivo") List<MultipartFile> arquivos,
                                                                    @RequestParam(required = false)
                                                                    TipoDocumentoFinanceiro tipo,
                                                                    @Parameter(hidden = true) Usuario usuario);

    @Operation(summary = "Remover documento",
            description = "Só quem enviou remove, salvo com `financeiro:documento:gerir`.")
    @ApiResponse(responseCode = "204", description = "Documento removido")
    ResponseEntity<Void> removerDocumento(@PathVariable UUID id, @PathVariable UUID idDocumento);

    @Operation(summary = "Baixar documento")
    @ApiResponse(responseCode = "200", description = "Conteúdo do documento")
    ResponseEntity<Resource> baixarDocumento(@PathVariable UUID id, @PathVariable UUID idDocumento);

    @Operation(summary = "Listar documentos da solicitação")
    @ApiResponse(responseCode = "200", description = "Documentos retornados")
    ResponseEntity<List<DocumentoResumoResponse>> listarDocumentos(@PathVariable UUID id);

    @Operation(summary = "Buscar solicitação por ID")
    @ApiResponse(responseCode = "200", description = "Solicitação encontrada")
    ResponseEntity<SolicitacaoPagamentoResponse> buscarPorId(@PathVariable UUID id);

    @Operation(summary = "Buscar solicitação pelo número")
    @ApiResponse(responseCode = "200", description = "Solicitação encontrada")
    ResponseEntity<SolicitacaoPagamentoResponse> buscarPorNumero(@PathVariable Long numero);

    @Operation(summary = "Exportar solicitações de pagamento em CSV resumido",
            description = "Aplica os mesmos filtros opcionais e o mesmo escopo da listagem. Gera CSV UTF-8 "
                    + "com BOM e separador ponto e vírgula, em streaming, limitado às primeiras 10.000 "
                    + "solicitações ordenadas por criação decrescente, incluindo a compra vinculada.")
    @ApiResponse(responseCode = "200", description = "Arquivo `solicitacoes_pagamento_resumo.csv`")
    @ApiResponse(responseCode = "403", description = "Sem `financeiro:pagamento:read`")
    ResponseEntity<StreamingResponseBody> exportarResumo(@ParameterObject SolicitacaoPagamentoFilter filtro);

    @Operation(summary = "Exportar solicitações de pagamento em CSV detalhado",
            description = "Inclui os dados resumidos, campos canônicos do formulário, fornecedor, desfecho, "
                    + "cancelamento, auditoria, documentos e detalhes da compra vinculada. Aplica os mesmos "
                    + "filtros da listagem e limita o arquivo a 10.000 solicitações.")
    @ApiResponse(responseCode = "200", description = "Arquivo `solicitacoes_pagamento_detalhado.csv`")
    @ApiResponse(responseCode = "403", description = "Sem `financeiro:pagamento:read`")
    ResponseEntity<StreamingResponseBody> exportarDetalhado(@ParameterObject SolicitacaoPagamentoFilter filtro);

    @Operation(summary = "Listar solicitações de pagamento",
            description = "Use `sort=fornecedor,asc` para ordenar pelo fornecedor. A busca global também "
                    + "encontra dados bancários protegidos, linha digitável, PIX e códigos dos anexos.")
    @ApiResponse(responseCode = "200", description = "Listagem retornada")
    ResponseEntity<PageResponse<SolicitacaoPagamentoResponse>> listar(
            @ParameterObject SolicitacaoPagamentoFilter filtro, @ParameterObject Pageable pageable);
}
