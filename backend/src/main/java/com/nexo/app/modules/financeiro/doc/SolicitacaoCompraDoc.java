package com.nexo.app.modules.financeiro.doc;

import com.nexo.app.modules.financeiro.application.dto.AprovacaoRequest;
import com.nexo.app.modules.financeiro.application.dto.EventoSolicitacaoResponse;
import com.nexo.app.modules.financeiro.application.dto.MotivoRequest;
import com.nexo.app.modules.financeiro.application.dto.SolicitacaoCompraFilter;
import com.nexo.app.modules.financeiro.application.dto.SolicitacaoCompraResponse;
import com.nexo.app.modules.financeiro.application.dto.DocumentoResumoResponse;
import com.nexo.app.modules.financeiro.application.dto.SolicitacaoRequest;
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
 * Solicitações de compra: preenchimento, submissão, decisão e documentos.
 *
 * <p>O corpo do preenchimento é o mapa {@code valores}, com as chaves publicadas
 * pelo schema da variante. O servidor valida cada chave contra esse schema e
 * devolve os erros campo a campo na propriedade {@code erros} do Problem Details.</p>
 *
 * <p>Os arquivos vivem no repositório documental e apenas apontam para a
 * solicitação. Os endpoints daqui são atalhos que já herdam o contexto do
 * formulário; a busca, a conferência e a correção dos metadados ficam em
 * {@code /v1/financeiro/documentos}.</p>
 */
@Tag(name = "Financeiro — Solicitações de Compra",
        description = "Pedidos de autorização para adquirir material ou serviço. O preenchimento segue o "
                + "schema da variante escolhida; quem decide é definido por permissão.")
public interface SolicitacaoCompraDoc {

    @Operation(summary = "Abrir solicitação de compra (rascunho)",
            description = "Cria a solicitação em RASCUNHO, para anexar orçamentos e completar o formulário "
                    + "em etapas sem ocupar a fila da diretoria.")
    @ApiResponse(responseCode = "201", description = "Rascunho criado")
    @ApiResponse(responseCode = "422", description = "Preenchimento inválido — veja a propriedade `erros`")
    ResponseEntity<SolicitacaoCompraResponse> criar(@RequestBody SolicitacaoRequest request,
                                                    @Parameter(hidden = true) Usuario usuario);

    @Operation(summary = "Atualizar o preenchimento",
            description = "Substitui o conteúdo por completo. Permitido enquanto não houver decisão.")
    @ApiResponse(responseCode = "200", description = "Solicitação atualizada")
    @ApiResponse(responseCode = "422", description = "Preenchimento inválido ou solicitação já finalizada")
    ResponseEntity<SolicitacaoCompraResponse> atualizar(@PathVariable UUID id,
                                                        @RequestBody SolicitacaoRequest request);

    @Operation(summary = "Submeter para aprovação")
    @ApiResponse(responseCode = "200", description = "Solicitação submetida")
    @ApiResponse(responseCode = "422", description = "Solicitação fora do estado RASCUNHO ou sem valor")
    ResponseEntity<SolicitacaoCompraResponse> submeter(@PathVariable UUID id);

    @Operation(summary = "Aprovar solicitação",
            description = "O valor aprovado pode ser menor que o solicitado, nunca maior: autorizar mais do "
                    + "que se pediu é decidir sobre algo que ninguém apresentou.")
    @ApiResponse(responseCode = "200", description = "Solicitação aprovada")
    @ApiResponse(responseCode = "422", description = "Estado inválido para decisão, ou valor acima do solicitado")
    ResponseEntity<SolicitacaoCompraResponse> aprovar(@PathVariable UUID id, @RequestBody AprovacaoRequest request,
                                                      @Parameter(hidden = true) Usuario usuario);

    @Operation(summary = "Rejeitar solicitação", description = "Exige justificativa.")
    @ApiResponse(responseCode = "200", description = "Solicitação rejeitada")
    ResponseEntity<SolicitacaoCompraResponse> rejeitar(@PathVariable UUID id, @RequestBody MotivoRequest request,
                                                       @Parameter(hidden = true) Usuario usuario);

    @Operation(summary = "Desfazer decisão",
            description = "Devolve uma solicitação aprovada ou rejeitada à fila de aprovação. Exige motivo, "
                    + "registra a decisão desfeita no histórico e avisa quem pediu e quem tinha decidido.")
    @ApiResponse(responseCode = "200", description = "Solicitação de volta a aguardar aprovação")
    @ApiResponse(responseCode = "422", description = "Solicitação sem decisão a desfazer ou com pagamento vinculado em andamento")
    ResponseEntity<SolicitacaoCompraResponse> desfazerDecisao(@PathVariable UUID id, @RequestBody MotivoRequest request,
                                                              @Parameter(hidden = true) Usuario usuario);

    @Operation(summary = "Histórico da solicitação",
            description = "Transições em ordem cronológica: abertura, envio, decisões, decisões desfeitas e cancelamento.")
    ResponseEntity<List<EventoSolicitacaoResponse>> historico(@PathVariable UUID id);

    @Operation(summary = "Cancelar solicitação",
            description = "Desistência do solicitante, possível enquanto não houver decisão.")
    @ApiResponse(responseCode = "200", description = "Solicitação cancelada")
    ResponseEntity<SolicitacaoCompraResponse> cancelar(@PathVariable UUID id, @RequestBody MotivoRequest request,
                                                       @Parameter(hidden = true) Usuario usuario);

    @Operation(summary = "Anexar um ou mais documentos",
            description = "Recebe uma ou mais partes multipart chamadas `arquivo`; enviar apenas uma "
                    + "continua suportado. Aceita imagem, PDF, XML, Word e Excel, até 15 MB por arquivo. "
                    + "Cada documento entra no repositório financeiro já vinculado a esta solicitação e herdando dela o que "
                    + "ela sabe: fornecedor, valor, base, centro de custo, obra e vencimento "
                    + "**É permitido depois da aprovação** — a nota fiscal e o boleto chegam justamente "
                    + "aí, e prendê-los às regras de edição da solicitação os deixaria de fora.")
    @ApiResponse(responseCode = "201", description = "Documento(s) adicionado(s)")
    @ApiResponse(responseCode = "422", description = "Formato não suportado ou arquivo acima do limite")
    ResponseEntity<SolicitacaoCompraResponse> adicionarDocumento(@PathVariable UUID id,
                                                                 @RequestPart("arquivo") List<MultipartFile> arquivos,
                                                                 @RequestParam(required = false)
                                                                 TipoDocumentoFinanceiro tipo,
                                                                 @Parameter(hidden = true) Usuario usuario);

    @Operation(summary = "Remover documento",
            description = "Retira o documento do repositório. Só quem enviou remove, salvo com "
                    + "`financeiro:documento:gerir`.")
    @ApiResponse(responseCode = "204", description = "Documento removido")
    ResponseEntity<Void> removerDocumento(@PathVariable UUID id, @PathVariable UUID idDocumento);

    @Operation(summary = "Baixar documento", description = "Conteúdo transmitido pelo backend, protegido por JWT.")
    @ApiResponse(responseCode = "200", description = "Conteúdo do documento")
    ResponseEntity<Resource> baixarDocumento(@PathVariable UUID id, @PathVariable UUID idDocumento);

    @Operation(summary = "Listar documentos da solicitação",
            description = "Metadados dos documentos vinculados, na ordem em que chegaram.")
    @ApiResponse(responseCode = "200", description = "Documentos retornados")
    ResponseEntity<List<DocumentoResumoResponse>> listarDocumentos(@PathVariable UUID id);

    @Operation(summary = "Buscar solicitação por ID")
    @ApiResponse(responseCode = "200", description = "Solicitação encontrada")
    ResponseEntity<SolicitacaoCompraResponse> buscarPorId(@PathVariable UUID id);

    @Operation(summary = "Buscar solicitação pelo número",
            description = "Número sequencial legível, que é como as equipes se referem à solicitação.")
    @ApiResponse(responseCode = "200", description = "Solicitação encontrada")
    ResponseEntity<SolicitacaoCompraResponse> buscarPorNumero(@PathVariable Long numero);

    @Operation(summary = "Exportar solicitações de compra em CSV resumido",
            description = "Aplica os mesmos filtros opcionais e o mesmo escopo da listagem. Gera CSV UTF-8 "
                    + "com BOM e separador ponto e vírgula, em streaming, limitado às primeiras 10.000 "
                    + "solicitações ordenadas por criação decrescente.")
    @ApiResponse(responseCode = "200", description = "Arquivo `solicitacoes_compra_resumo.csv`")
    @ApiResponse(responseCode = "403", description = "Sem `financeiro:compra:read`")
    ResponseEntity<StreamingResponseBody> exportarResumo(@ParameterObject SolicitacaoCompraFilter filtro);

    @Operation(summary = "Exportar solicitações de compra em CSV detalhado",
            description = "Inclui os dados resumidos, campos canônicos do formulário, fornecedor, desfecho, "
                    + "cancelamento, auditoria e metadados dos documentos. Aplica os mesmos filtros da "
                    + "listagem e limita o arquivo a 10.000 solicitações.")
    @ApiResponse(responseCode = "200", description = "Arquivo `solicitacoes_compra_detalhado.csv`")
    @ApiResponse(responseCode = "403", description = "Sem `financeiro:compra:read`")
    ResponseEntity<StreamingResponseBody> exportarDetalhado(@ParameterObject SolicitacaoCompraFilter filtro);

    @Operation(summary = "Listar solicitações de compra",
            description = "Use `status=APROVADO` para popular o vínculo de uma solicitação de pagamento e "
                    + "`apenasMinhas=true` para a visão do solicitante. `sort=fornecedor,asc` ordena pelo "
                    + "fornecedor. A busca global também encontra dados bancários protegidos e códigos dos anexos.")
    @ApiResponse(responseCode = "200", description = "Listagem retornada")
    ResponseEntity<PageResponse<SolicitacaoCompraResponse>> listar(@ParameterObject SolicitacaoCompraFilter filtro,
                                                                    @ParameterObject Pageable pageable);
}
