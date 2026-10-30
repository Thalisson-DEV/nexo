package com.nexo.app.modules.rh.doc;

import com.nexo.app.modules.rh.application.dto.MetaFaturamentoFilter;
import com.nexo.app.modules.rh.application.dto.MetaFaturamentoRequest;
import com.nexo.app.modules.rh.application.dto.MetaFaturamentoResponse;
import com.nexo.app.modules.rh.application.dto.MetaFaturamentoUpdateRequest;
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

@Tag(name = "RH - Metas de Faturamento",
        description = "Metas de faturamento das equipes por ciclo comercial (26 a 25) ou por mês civil")
@SecurityRequirement(name = "bearerAuth")
public interface MetaFaturamentoControllerDoc {

    @Operation(
            summary = "Cadastrar metas de faturamento",
            description = """
                    Cria uma meta para cada equipe informada, com o mesmo valor e a mesma vigência
                    (tudo ou nada).

                    O valor vale por janela, definida pela periodicidade: CICLO_COMERCIAL vai do dia 26
                    de um mês ao dia 25 do seguinte (o ciclo das equipes do processo comercial; a
                    competência é o mês em que ele termina) e MENSAL é o mês civil. A vigência é contada
                    em janelas inteiras: começa no primeiro dia de uma janela e, se tiver fim, termina no
                    último dia de outra. Sem fim, a meta vale até ser encerrada.

                    Com substituirVigentes = true, a meta sem prazo que a equipe já tem é encerrada no fim
                    da janela anterior — o jeito de reajustar a meta a partir de um ciclo mantendo o
                    histórico. Sem essa opção, uma vigência sobreposta é rejeitada.""")
    @ApiResponse(responseCode = "201", description = "Metas cadastradas")
    @ApiResponse(responseCode = "422", description = "Vigência desalinhada às janelas ou sobreposta a outra meta ativa")
    ResponseEntity<List<MetaFaturamentoResponse>> criar(MetaFaturamentoRequest request);

    @Operation(summary = "Buscar uma meta por ID")
    ResponseEntity<MetaFaturamentoResponse> buscarPorId(UUID id);

    @Operation(summary = "Listar metas com filtros dinâmicos",
            description = "Base operacional e processo filtram pelas equipes atuais desses recortes.")
    ResponseEntity<PageResponse<MetaFaturamentoResponse>> listar(
            @ParameterObject MetaFaturamentoFilter filter, Pageable pageable);

    @Operation(summary = "Atualizar uma meta", description = "Altera valor, periodicidade, vigência e descrição. A equipe é imutável.")
    ResponseEntity<MetaFaturamentoResponse> atualizar(UUID id, MetaFaturamentoUpdateRequest request);

    @Operation(summary = "Desativar (soft delete) uma meta")
    ResponseEntity<Void> desativar(UUID id);

    @Operation(summary = "Reativar uma meta")
    ResponseEntity<Void> ativar(UUID id);
}
