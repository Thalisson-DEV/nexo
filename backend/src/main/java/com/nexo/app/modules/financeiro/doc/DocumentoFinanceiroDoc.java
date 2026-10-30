package com.nexo.app.modules.financeiro.doc;

import com.nexo.app.modules.financeiro.application.dto.DocumentoFinanceiroFilter;
import com.nexo.app.modules.financeiro.application.dto.DocumentoFinanceiroResponse;
import com.nexo.app.modules.financeiro.application.dto.DocumentoRequest;
import com.nexo.app.modules.financeiro.application.dto.MotivoRequest;
import com.nexo.app.modules.financeiro.domain.TipoSolicitacao;
import com.nexo.app.modules.iam.domain.Usuario;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Repositório documental do financeiro — o lugar onde nota fiscal, boleto,
 * comprovante e contrato ficam procuráveis pela contabilidade.
 *
 * <p>O documento é independente da solicitação: pode nascer vinculado a uma compra
 * ou pagamento, pode nascer avulso e ser vinculado depois, e pode ser enviado
 * <b>após a aprovação</b> — que é justamente quando a nota fiscal e o boleto
 * chegam.</p>
 */
@Tag(name = "Financeiro - Documentos",
        description = "Repositório de notas fiscais, boletos, comprovantes e contratos, "
                + "com busca, conferência contábil e arquivamento")
@SecurityRequirement(name = "bearerAuth")
public interface DocumentoFinanceiroDoc {

    @Operation(
            summary = "Envia um documento avulso",
            description = """
                    Recebe um arquivo sem solicitação de origem — o boleto que chegou por e-mail, a
                    nota que o fornecedor mandou antes de existir pedido no sistema.

                    A requisição é `multipart/form-data` com duas partes:
                    - `arquivo`: o binário (imagens, PDF, XML, Word ou Excel; até 15 MB);
                    - `dados`: JSON opcional com os metadados (`DocumentoRequest`).

                    Quase todo metadado é opcional de propósito: classificar na porta de entrada
                    encarece o envio, e documento fora do repositório é pior do que documento sem
                    número dentro dele. O que faltar aparece no dashboard como qualidade de dados.

                    A competência, quando omitida, é deduzida do vencimento e, na falta dele, da
                    emissão. O conteúdo é resumido em SHA-256: reenviar o mesmo arquivo faz o
                    campo `duplicado` acender nas duas cópias.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Documento armazenado"),
            @ApiResponse(responseCode = "400", description = "Arquivo vazio, grande demais ou de tipo não aceito"),
            @ApiResponse(responseCode = "403", description = "Sem permissão financeiro:documento:write")
    })
    ResponseEntity<DocumentoFinanceiroResponse> enviar(
            @RequestPart("arquivo") MultipartFile arquivo,
            @RequestPart(value = "dados", required = false) @Valid DocumentoRequest dados,
            @Parameter(hidden = true) Usuario usuario);

    @Operation(summary = "Corrige os metadados de um documento",
            description = "Substitui por completo a classificação do documento. Um documento "
                    + "arquivado recusa a alteração: reabra-o antes, e a reabertura fica registrada.")
    ResponseEntity<DocumentoFinanceiroResponse> atualizar(@PathVariable UUID id,
                                                          @Valid @RequestBody DocumentoRequest request);

    @Operation(summary = "Vincula o documento a uma solicitação",
            description = "Aponta um documento avulso para a compra ou o pagamento a que ele "
                    + "pertence, depois que a origem foi identificada.")
    ResponseEntity<DocumentoFinanceiroResponse> vincular(
            @PathVariable UUID id,
            @Parameter(description = "Domínio da solicitação de origem") TipoSolicitacao tipo,
            @Parameter(description = "Identificador da solicitação de origem") UUID solicitacaoId);

    @Operation(summary = "Desfaz o vínculo com a solicitação",
            description = "Usado quando o documento foi anexado à solicitação errada.")
    ResponseEntity<DocumentoFinanceiroResponse> desvincular(@PathVariable UUID id);

    @Operation(summary = "Aponta pendência no documento",
            description = "Recusa o documento com um motivo obrigatório, devolvendo a ação para "
                    + "quem o enviou. A situação `COM_PENDENCIA` é separada de `PENDENTE` porque as "
                    + "duas cobram pessoas diferentes.")
    ResponseEntity<DocumentoFinanceiroResponse> apontarPendencia(@PathVariable UUID id,
                                                                 @Valid @RequestBody MotivoRequest request,
                                                                 @Parameter(hidden = true) Usuario usuario);

    @Operation(summary = "Arquiva o documento",
            description = "Encerra o documento e o retira das filas de trabalho. Só o que já foi "
                    + "conferido pode ser arquivado — arquivar sem conferir esconderia o documento "
                    + "sem que ninguém o tivesse olhado.")
    ResponseEntity<DocumentoFinanceiroResponse> arquivar(@PathVariable UUID id);

    @Operation(summary = "Reabre o documento",
            description = "Devolve o documento à fila de conferência, limpando a decisão anterior.")
    ResponseEntity<DocumentoFinanceiroResponse> reabrir(@PathVariable UUID id);

    @Operation(summary = "Remove o documento",
            description = "Desativa o registro (soft delete, preservando a trilha de quem enviou) e "
                    + "apaga o arquivo do storage. Só quem enviou remove, salvo com "
                    + "`financeiro:documento:gerir`.")
    ResponseEntity<Void> remover(@PathVariable UUID id);

    @Operation(summary = "Baixa o conteúdo do documento",
            description = "O download é servido pelo backend, sob o mesmo JWT; a chave do objeto no "
                    + "storage nunca é exposta.")
    ResponseEntity<Resource> baixar(@PathVariable UUID id);

    @Operation(summary = "Detalha um documento")
    ResponseEntity<DocumentoFinanceiroResponse> buscarPorId(@PathVariable UUID id);

    @Operation(
            summary = "Busca no repositório documental",
            description = """
                    Listagem paginada com os recortes que a contabilidade usa no dia a dia:
                    natureza, situação de conferência, emissor, competência contábil, emissão,
                    vencimento, obra, centro de custo e faixa de valor.

                    Recortes prontos que respondem perguntas inteiras:
                    - `apenasVencidos`: venceu e continua em aberto na conferência;
                    - `apenasSemIdentificacao`: nota ou boleto sem número informado — não serve
                      como comprovação;
                    - `apenasAvulsos`: documentos que ainda não foram ligados a nenhuma solicitação.

                    A busca rápida (`buscaGlobal`) cobre nome do arquivo, número/chave do documento,
                    código pagável, emissor digitado e descrição. Para o fornecedor cadastrado existe o filtro
                    dedicado, que é exato e não depende de o nome ter sido digitado igual.

                    A competência é guardada no dia 1 do mês; qualquer dia informado nos limites é
                    normalizado, de modo que "de 15/08" não deixe agosto inteiro de fora.""")
    ResponseEntity<PageResponse<DocumentoFinanceiroResponse>> listar(
            @ParameterObject DocumentoFinanceiroFilter filtro, @ParameterObject Pageable pageable);
}
