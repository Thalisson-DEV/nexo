package com.nexo.app.modules.sst.doc;

import com.nexo.app.modules.sst.application.dto.AtividadeSstFilter;
import com.nexo.app.modules.sst.application.dto.AtividadeSstRequest;
import com.nexo.app.modules.sst.application.dto.AtividadeSstResponse;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

@Tag(name = "SST - Atividades",
        description = "Atividades pré-cadastradas sob as quais os eventos de SST acontecem")
@SecurityRequirement(name = "bearerAuth")
public interface AtividadeSstControllerDoc {

    @Operation(
            summary = "Cadastrar uma atividade",
            description = """
                    A atividade é a natureza do que estava sendo feito — trajeto, detonação,
                    poda, lançamento de cabo —, não uma execução específica.

                    O gestor responsável é opcional aqui e obrigatório no evento: enquanto a
                    atividade estiver sem gestor, o registro do evento cobra que ele seja
                    informado. Trocar o gestor da atividade vale para os próximos eventos e não
                    altera nenhum evento já registrado, porque o evento guarda uma cópia.

                    O nome é único entre as atividades ativas, comparado sem acento e sem
                    diferenciar maiúsculas.""")
    @ApiResponse(responseCode = "201", description = "Atividade cadastrada")
    @ApiResponse(responseCode = "422", description = "Nome duplicado ou gestor inexistente/inativo")
    ResponseEntity<AtividadeSstResponse> criar(AtividadeSstRequest request);

    @Operation(summary = "Buscar uma atividade por ID")
    ResponseEntity<AtividadeSstResponse> buscarPorId(UUID id);

    @Operation(summary = "Listar atividades com filtros dinâmicos")
    ResponseEntity<PageResponse<AtividadeSstResponse>> listar(
            @ParameterObject AtividadeSstFilter filter, Pageable pageable);

    @Operation(
            summary = "Listar as atividades ativas",
            description = "Sem paginação, em ordem alfabética — abastece o campo do formulário de registro de evento.")
    ResponseEntity<List<AtividadeSstResponse>> listarOpcoes();

    @Operation(summary = "Atualizar uma atividade")
    @ApiResponse(responseCode = "422", description = "Nome duplicado ou gestor inexistente/inativo")
    ResponseEntity<AtividadeSstResponse> atualizar(UUID id, AtividadeSstRequest request);

    @Operation(
            summary = "Desativar (soft delete) uma atividade",
            description = "A atividade deixa de ser oferecida em novos registros e continua exibida nos eventos antigos.")
    ResponseEntity<Void> desativar(UUID id);

    @Operation(summary = "Reativar uma atividade")
    ResponseEntity<Void> ativar(UUID id);
}
