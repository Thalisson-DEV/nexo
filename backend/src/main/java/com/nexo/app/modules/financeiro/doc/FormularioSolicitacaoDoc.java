package com.nexo.app.modules.financeiro.doc;

import com.nexo.app.modules.financeiro.application.dto.CampoCanonicoDisponivelResponse;
import com.nexo.app.modules.financeiro.application.dto.CampoRequest;
import com.nexo.app.modules.financeiro.application.dto.FormularioFilter;
import com.nexo.app.modules.financeiro.application.dto.FormularioRequest;
import com.nexo.app.modules.financeiro.application.dto.FormularioResumoResponse;
import com.nexo.app.modules.financeiro.application.dto.FormularioSchemaResponse;
import com.nexo.app.modules.financeiro.application.dto.FormularioUpdateRequest;
import com.nexo.app.modules.financeiro.application.dto.ReordenarRequest;
import com.nexo.app.modules.financeiro.domain.TipoSolicitacao;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * Configuração e leitura das variantes de formulário.
 *
 * <p>Todas as operações de escrita devolvem o schema completo já atualizado, para
 * que a tela de configuração reflita o resultado sem uma segunda chamada.</p>
 */
@Tag(name = "Financeiro — Formulários",
        description = "Variantes de formulário. O schema publicado descreve os campos na ordem de exibição, com "
                + "tipo, opções, limites, endpoints de referência e condições de visibilidade — o frontend "
                + "renderiza a partir dele, sem regras replicadas em código de tela.")
public interface FormularioSolicitacaoDoc {

    // ── Variante ────────────────────────────────────────────────────────────────

    @Operation(summary = "Criar variante de formulário")
    @ApiResponse(responseCode = "201", description = "Variante criada")
    @ApiResponse(responseCode = "422", description = "Código já utilizado neste tipo de solicitação")
    ResponseEntity<FormularioSchemaResponse> criar(@RequestBody FormularioRequest request);

    @Operation(summary = "Atualizar metadados da variante",
            description = "O código não muda: é a referência estável da variante.")
    @ApiResponse(responseCode = "200", description = "Variante atualizada")
    ResponseEntity<FormularioSchemaResponse> atualizar(@PathVariable UUID id,
                                                       @RequestBody FormularioUpdateRequest request);

    @Operation(summary = "Duplicar variante",
            description = "Cria uma cópia integral sob novo código. É o caminho seguro para evoluir um "
                    + "formulário já usado, preservando a leitura das solicitações históricas.")
    @ApiResponse(responseCode = "201", description = "Variante duplicada")
    ResponseEntity<FormularioSchemaResponse> duplicar(@PathVariable UUID id, @RequestBody FormularioRequest request);

    @Operation(summary = "Desativar variante",
            description = "Retira a variante da escolha do solicitante sem afetar o histórico.")
    @ApiResponse(responseCode = "204", description = "Variante desativada")
    ResponseEntity<Void> desativar(@PathVariable UUID id);

    @Operation(summary = "Reativar variante")
    @ApiResponse(responseCode = "204", description = "Variante reativada")
    ResponseEntity<Void> ativar(@PathVariable UUID id);

    // ── Campos ──────────────────────────────────────────────────────────────────

    @Operation(summary = "Adicionar campo",
            description = "Informe o `campoCanonico` desejado. Tipo, opções, limites e condição de exibição vêm "
                    + "do próprio campo do sistema; a configuração decide apenas rótulo, ajuda e "
                    + "obrigatoriedade.")
    @ApiResponse(responseCode = "201", description = "Campo adicionado")
    @ApiResponse(responseCode = "422", description = "Campo já presente na variante ou inaplicável ao tipo")
    ResponseEntity<FormularioSchemaResponse> adicionarCampo(@PathVariable UUID id,
                                                            @RequestBody CampoRequest request);

    @Operation(summary = "Atualizar campo",
            description = "Altera rótulo, ajuda e obrigatoriedade. O campo do sistema em si é "
                    + "imutável — é contrato com a coluna.")
    @ApiResponse(responseCode = "200", description = "Campo atualizado")
    ResponseEntity<FormularioSchemaResponse> atualizarCampo(@PathVariable UUID id, @PathVariable UUID idCampo,
                                                            @RequestBody CampoRequest request);

    @Operation(summary = "Remover campo")
    @ApiResponse(responseCode = "200", description = "Campo removido")
    @ApiResponse(responseCode = "422", description = "Outro campo depende deste para se exibir")
    ResponseEntity<FormularioSchemaResponse> removerCampo(@PathVariable UUID id, @PathVariable UUID idCampo);

    @Operation(summary = "Reordenar campos",
            description = "Informe a permutação completa dos IDs existentes. Permitido mesmo em formulários "
                    + "em uso: altera apenas a apresentação.")
    @ApiResponse(responseCode = "200", description = "Campos reordenados")
    ResponseEntity<FormularioSchemaResponse> reordenarCampos(@PathVariable UUID id,
                                                             @RequestBody ReordenarRequest request);

    // ── Consultas ───────────────────────────────────────────────────────────────

    @Operation(summary = "Obter o schema da variante",
            description = "Contrato completo para renderizar e validar a tela.")
    @ApiResponse(responseCode = "200", description = "Schema retornado")
    ResponseEntity<FormularioSchemaResponse> buscarSchema(@PathVariable UUID id);

    @Operation(summary = "Obter o schema pelo tipo e código",
            description = "Atalho para telas que referenciam a variante pelo código, ex.: COMPRA/FROTA.")
    @ApiResponse(responseCode = "200", description = "Schema retornado")
    @ApiResponse(responseCode = "404", description = "Variante não encontrada")
    ResponseEntity<FormularioSchemaResponse> buscarSchemaPorCodigo(@RequestParam TipoSolicitacao tipo,
                                                                    @RequestParam String codigo);

    @Operation(summary = "Listar variantes disponíveis de um tipo",
            description = "Alimenta a escolha do solicitante ao abrir uma nova solicitação; a variante padrão "
                    + "vem primeiro.")
    @ApiResponse(responseCode = "200", description = "Variantes retornadas")
    ResponseEntity<List<FormularioResumoResponse>> listarVariantes(@RequestParam TipoSolicitacao tipo);

    @Operation(summary = "Listar variantes com filtros e paginação")
    @ApiResponse(responseCode = "200", description = "Listagem retornada")
    ResponseEntity<PageResponse<FormularioResumoResponse>> listar(@ParameterObject FormularioFilter filtro,
                                                                   @ParameterObject Pageable pageable);

    @Operation(summary = "Listar campos do sistema aplicáveis à variante",
            description = "Catálogo para a tela de configuração, marcando os que já estão em uso — cada campo "
                    + "do sistema só pode aparecer uma vez por formulário.")
    @ApiResponse(responseCode = "200", description = "Catálogo retornado")
    ResponseEntity<List<CampoCanonicoDisponivelResponse>> listarCamposCanonicos(@PathVariable UUID id);
}
