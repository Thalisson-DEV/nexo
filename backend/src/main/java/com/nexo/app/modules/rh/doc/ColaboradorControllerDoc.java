package com.nexo.app.modules.rh.doc;

import com.nexo.app.modules.rh.ColaboradorResponse;
import com.nexo.app.modules.rh.application.dto.*;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.application.dto.LookupOptionDto;
import com.nexo.app.shared.application.dto.LookupPageDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Colaboradores", description = "Gestão de recursos humanos e funcionários")
@SecurityRequirement(name = "bearerAuth")
public interface ColaboradorControllerDoc {

    @Operation(summary = "Registrar novo colaborador", description = "Salva um colaborador com CPF e nomes cifrados 🔒")
    ResponseEntity<ColaboradorResponse> criar(ColaboradorRequest request);

    @Operation(summary = "Gerar acesso para colaborador", description = "Cria um usuário no IAM e vincula ao colaborador")
    ResponseEntity<ColaboradorResponse> gerarAcesso(VincularAcessoRequest request);

    @Operation(summary = "Atualizar colaborador", description = "Altera dados pessoais, processo e base do colaborador")
    ResponseEntity<ColaboradorResponse> atualizar(UUID id, ColaboradorUpdateRequest request);

    @Operation(summary = "Desativar colaborador", description = "Realiza o soft delete do colaborador")
    ResponseEntity<Void> desativar(UUID id);

    @Operation(summary = "Ativar colaborador", description = "Reativa o colaborador no sistema")
    ResponseEntity<Void> ativar(UUID id);

    @Operation(summary = "Buscar colaborador por ID")
    ResponseEntity<ColaboradorResponse> buscarPorId(UUID id);

    @Operation(summary = "Buscar opções de colaboradores",
            description = "Autocomplete por prefixo normalizado. Retorna somente id e nome, "
                    + "com limite máximo de 100 e consulta mínima de 2 caracteres.")
    ResponseEntity<LookupPageDto<LookupOptionDto>> searchOptions(
            @Parameter(description = "Prefixo de busca; acentos são ignorados",
                    schema = @Schema(minLength = 2)) String q,
            @Parameter(description = "Quantidade por página (padrão 50, máximo 100)",
                    schema = @Schema(defaultValue = "50", maximum = "100", minimum = "1")) Integer limit,
            @Parameter(description = "Cursor opaco da página anterior") String cursor);

    @Operation(summary = "Listar colaboradores com filtros dinâmicos", description = "Permite filtrar por status, base operacional, processo, função e datas")
    ResponseEntity<PageResponse<ColaboradorResponse>> listar(@ParameterObject ColaboradorFilter filter, @ParameterObject Pageable pageable);

    @Deprecated(since = "v1.1.0", forRemoval = true)
    @Operation(summary = "Listar Coordenadores e Supervisores ativos (Depreciado)", description = "Retorna lista simplificada para popular filtros no frontend. Recomendado utilizar os endpoints de /coordenadores e /supervisores.")
    ResponseEntity<List<ColaboradorMinResponse>> listarCoordenadoresESupervisores();

    @Operation(summary = "Validar importação de colaboradores em lote", description = "Valida arquivo CSV (separador ';' e UTF-8) com dados de colaboradores. Campos: primeiro_nome;ultimo_nome;nome_completo;cpf;nome_processo;nome_base;nome_funcao;data_contratacao. Limite: 500 linhas.")
    ResponseEntity<ImportacaoColaboradorResponse> importar(
        @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
        MultipartFile arquivo);

    @Operation(summary = "Confirmar importação de colaboradores", description = "Revalida o lote, ignora os registros com erro e inicia a persistência assíncrona somente dos colaboradores válidos.")
    ResponseEntity<ImportacaoColaboradorResponse> confirmarImportacao(ConfirmarImportacaoRequest request);

    @Operation(summary = "Baixar modelo CSV para importação de colaboradores", description = "Disponibiliza o arquivo CSV de exemplo com cabeçalho e formato exatos para importação.")
    ResponseEntity<byte[]> baixarModeloCsv();
}
