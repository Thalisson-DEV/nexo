package com.nexo.app.modules.financeiro.doc;

import com.nexo.app.modules.financeiro.application.dto.FornecedorFilter;
import com.nexo.app.modules.financeiro.application.dto.FornecedorRequest;
import com.nexo.app.modules.financeiro.application.dto.FornecedorResponse;
import com.nexo.app.modules.financeiro.application.dto.MotivoRequest;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@Tag(name = "Financeiro — Fornecedores",
        description = "Cadastro de fornecedores e curadoria pelo financeiro. Fornecedores criados por "
                + "solicitantes nascem PENDENTE e já podem ser usados; a homologação exige razão social e CNPJ.")
public interface FornecedorDoc {

    @Operation(summary = "Cadastrar fornecedor",
            description = "Cria o fornecedor com status PENDENTE. Apenas o nome é obrigatório, para não "
                    + "interromper o preenchimento de uma solicitação.")
    @ApiResponse(responseCode = "201", description = "Fornecedor cadastrado")
    @ApiResponse(responseCode = "422", description = "CNPJ/CPF já cadastrado ou inválido")
    ResponseEntity<FornecedorResponse> criar(@RequestBody FornecedorRequest request);

    @Operation(summary = "Atualizar fornecedor")
    @ApiResponse(responseCode = "200", description = "Fornecedor atualizado")
    @ApiResponse(responseCode = "404", description = "Fornecedor não encontrado")
    ResponseEntity<FornecedorResponse> atualizar(@PathVariable UUID id, @RequestBody FornecedorRequest request);

    @Operation(summary = "Homologar fornecedor",
            description = "Confirma a conferência dos dados pelo financeiro. Exige razão social e CNPJ/CPF.")
    @ApiResponse(responseCode = "200", description = "Fornecedor homologado")
    @ApiResponse(responseCode = "422", description = "Dados insuficientes para homologação")
    ResponseEntity<FornecedorResponse> homologar(@PathVariable UUID id);

    @Operation(summary = "Bloquear fornecedor",
            description = "Impede novas solicitações para este fornecedor, preservando o histórico.")
    @ApiResponse(responseCode = "200", description = "Fornecedor bloqueado")
    ResponseEntity<FornecedorResponse> bloquear(@PathVariable UUID id, @RequestBody MotivoRequest request);

    @Operation(summary = "Desbloquear fornecedor", description = "Devolve o fornecedor à curadoria (PENDENTE).")
    @ApiResponse(responseCode = "200", description = "Fornecedor desbloqueado")
    ResponseEntity<FornecedorResponse> desbloquear(@PathVariable UUID id);

    @Operation(summary = "Desativar fornecedor", description = "Inativação lógica (soft delete).")
    @ApiResponse(responseCode = "204", description = "Fornecedor desativado")
    ResponseEntity<Void> desativar(@PathVariable UUID id);

    @Operation(summary = "Reativar fornecedor")
    @ApiResponse(responseCode = "204", description = "Fornecedor reativado")
    ResponseEntity<Void> ativar(@PathVariable UUID id);

    @Operation(summary = "Buscar fornecedor por ID")
    @ApiResponse(responseCode = "200", description = "Fornecedor encontrado")
    @ApiResponse(responseCode = "404", description = "Fornecedor não encontrado")
    ResponseEntity<FornecedorResponse> buscarPorId(@PathVariable UUID id);

    @Operation(summary = "Listar fornecedores",
            description = "Busca rápida por nome fantasia, razão social ou documento. Use "
                    + "`apenasUtilizaveis=true` para popular o combo do formulário.")
    @ApiResponse(responseCode = "200", description = "Listagem retornada")
    ResponseEntity<PageResponse<FornecedorResponse>> listar(@ParameterObject FornecedorFilter filtro,
                                                            @ParameterObject Pageable pageable);
}
