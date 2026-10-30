package com.nexo.app.modules.sst.doc;

import com.nexo.app.modules.sst.application.dto.AtualizarFormularioDadosRequest;
import com.nexo.app.modules.sst.application.dto.CriarFormularioRequest;
import com.nexo.app.modules.sst.application.dto.FormularioInspecaoFilter;
import com.nexo.app.modules.sst.application.dto.FormularioInspecaoResponse;
import com.nexo.app.modules.sst.application.dto.FormularioInspecaoResumoResponse;
import com.nexo.app.modules.sst.application.dto.ReordenarRequest;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

@Tag(name = "SST - Formulários de Inspeção", description = "Cadastro de formulários dinâmicos de inspeção (grupos, perguntas e opções de resposta)")
@SecurityRequirement(name = "bearerAuth")
public interface FormularioInspecaoControllerDoc {

    @Operation(
            summary = "Criar um formulário de inspeção",
            description = "Cria o formulário completo em uma única requisição: grupos, perguntas e opções de resposta. Cada opção pode ser marcada como geradora de desvio.")
    @ApiResponse(responseCode = "201", description = "Formulário criado com sucesso")
    ResponseEntity<FormularioInspecaoResponse> criar(CriarFormularioRequest request);

    @Operation(summary = "Buscar um formulário por ID (árvore completa)")
    ResponseEntity<FormularioInspecaoResponse> buscarPorId(UUID id);

    @Operation(summary = "Listar formulários com filtros dinâmicos (resumo)")
    ResponseEntity<PageResponse<FormularioInspecaoResumoResponse>> listar(@ParameterObject FormularioInspecaoFilter filter, Pageable pageable);

    @Operation(
            summary = "Atualizar a estrutura de um formulário",
            description = "Substitui grupos/perguntas/opções. Bloqueado caso o formulário já tenha sido utilizado em alguma inspeção.")
    ResponseEntity<FormularioInspecaoResponse> atualizarEstrutura(UUID id, CriarFormularioRequest request);

    @Operation(summary = "Atualizar apenas os metadados (título/descrição) do formulário")
    ResponseEntity<FormularioInspecaoResponse> atualizarDados(UUID id, AtualizarFormularioDadosRequest request);

    @Operation(summary = "Duplicar um formulário (gerar nova versão editável)")
    @ApiResponse(responseCode = "201", description = "Formulário duplicado com sucesso")
    ResponseEntity<FormularioInspecaoResponse> duplicar(UUID id);

    @Operation(
            summary = "Reordenar os grupos do formulário",
            description = "Define a nova ordem dos grupos pela lista de IDs. Preserva os IDs e é permitido mesmo após o formulário ter sido usado em inspeções.")
    ResponseEntity<FormularioInspecaoResponse> reordenarGrupos(UUID id, ReordenarRequest request);

    @Operation(
            summary = "Reordenar as perguntas de um grupo",
            description = "Define a nova ordem das perguntas de um grupo pela lista de IDs. Preserva os IDs e é permitido mesmo após o uso do formulário.")
    ResponseEntity<FormularioInspecaoResponse> reordenarPerguntas(UUID id, UUID idGrupo, ReordenarRequest request);

    @Operation(
            summary = "Reordenar as opções de uma pergunta",
            description = "Define a nova ordem das opções de resposta de uma pergunta pela lista de IDs. Preserva os IDs e é permitido mesmo após o uso do formulário.")
    ResponseEntity<FormularioInspecaoResponse> reordenarOpcoes(UUID id, UUID idGrupo, UUID idPergunta, ReordenarRequest request);

    @Operation(summary = "Desativar (soft delete) um formulário")
    ResponseEntity<Void> desativar(UUID id);

    @Operation(summary = "Reativar um formulário")
    ResponseEntity<Void> ativar(UUID id);
}
