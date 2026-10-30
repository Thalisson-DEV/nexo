package com.nexo.app.modules.rh.doc;

import com.nexo.app.modules.rh.EquipeMembroResponse;
import com.nexo.app.modules.rh.EquipeResponse;
import com.nexo.app.modules.rh.application.dto.*;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Equipes", description = "Gestão de unidades operacionais (equipes de campo)")
@SecurityRequirement(name = "bearerAuth")
public interface EquipeControllerDoc {

    @Operation(summary = "Criar uma nova equipe")
    @ApiResponse(responseCode = "201", description = "Equipe criada com sucesso")
    ResponseEntity<EquipeResponse> criar(@RequestBody @Valid EquipeRequest request);

    @Operation(summary = "Validar arquivo CSV de importação de equipes")
    @ApiResponse(responseCode = "200", description = "Arquivo validado com sucesso")
    ResponseEntity<ImportacaoEquipeResponse> validarImportacao(@RequestParam("arquivo") MultipartFile arquivo);

    @Operation(summary = "Confirmar importação de equipes após validação")
    @ApiResponse(responseCode = "200", description = "Importação iniciada com sucesso")
    ResponseEntity<ImportacaoEquipeResponse> confirmarImportacao(@RequestBody @Valid ConfirmarImportacaoEquipeRequest request);

    @Operation(summary = "Atualizar uma equipe existente")
    ResponseEntity<EquipeResponse> atualizar(@PathVariable UUID id, @RequestBody @Valid EquipeRequest request);

    @Operation(summary = "Buscar equipe por ID")
    ResponseEntity<EquipeResponse> buscarPorId(@PathVariable UUID id);

    @Operation(summary = "Listar equipes com filtros dinâmicos", description = "Permite filtrar por código, tipo, base, coordenador, supervisor, membros e responsáveis")
    ResponseEntity<PageResponse<EquipeResponse>> listar(@ParameterObject EquipeFilter filter, @ParameterObject Pageable pageable);

    @Operation(summary = "Desativar uma equipe (Soft Delete)")
    @ApiResponse(responseCode = "204", description = "Equipe desativada com sucesso")
    ResponseEntity<Void> desativar(@PathVariable UUID id);

    @Operation(summary = "Ativar uma equipe")
    @ApiResponse(responseCode = "200", description = "Equipe ativada com sucesso")
    ResponseEntity<Void> ativar(@PathVariable UUID id);

    @Operation(summary = "Alocar membro na equipe", description = "Adiciona um colaborador como Responsável ou Parceiro")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Membro alocado com sucesso"),
        @ApiResponse(responseCode = "400", description = "Regra de negócio violada (ex: já existe responsável)"),
        @ApiResponse(responseCode = "404", description = "Equipe ou Colaborador não encontrado")
    })
    ResponseEntity<Void> alocarMembro(@PathVariable UUID id, @RequestBody @Valid EquipeMembroRequest request);

    @Operation(summary = "Alocar múltiplos membros na equipe (Lote)", description = "Adiciona vários colaboradores de uma vez. A operação é atômica.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Membros alocados com sucesso"),
        @ApiResponse(responseCode = "400", description = "Regra de negócio violada (ex: mais de um responsável na lista ou na equipe)"),
        @ApiResponse(responseCode = "404", description = "Equipe ou algum Colaborador não encontrado")
    })
    ResponseEntity<Void> alocarMembros(@PathVariable UUID id, @RequestBody @Valid List<EquipeMembroRequest> requests);

    @Operation(summary = "Desalocar membro da equipe", description = "Encerra o vínculo ativo de um colaborador com a equipe")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Membro desalocado com sucesso"),
        @ApiResponse(responseCode = "404", description = "Equipe ou Colaborador não encontrado")
    })
    ResponseEntity<Void> desalocarMembro(@PathVariable UUID id, @PathVariable UUID colaboradorId);

    @Operation(summary = "Transferir responsabilidade", description = "Passa a responsabilidade da equipe para outro colaborador que já é membro")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Responsabilidade transferida com sucesso"),
        @ApiResponse(responseCode = "400", description = "Regra de negócio violada (ex: novo responsável não é membro)"),
        @ApiResponse(responseCode = "404", description = "Equipe ou Colaborador não encontrado")
    })
    ResponseEntity<Void> transferirResponsabilidade(@PathVariable UUID id, @PathVariable UUID colaboradorId);

    @Operation(summary = "Listar membros ativos da equipe")
    ResponseEntity<List<EquipeMembroResponse>> listarMembrosAtivos(@PathVariable UUID id);

    @Operation(summary = "Baixar modelo CSV para importação de equipes", description = "Disponibiliza o arquivo CSV de exemplo com cabeçalho e formato exatos para importação.")
    ResponseEntity<byte[]> baixarModeloCsv();
}
