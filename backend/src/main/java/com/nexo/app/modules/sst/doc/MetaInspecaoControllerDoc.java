package com.nexo.app.modules.sst.doc;

import com.nexo.app.modules.sst.application.dto.AcompanhamentoMetaFilter;
import com.nexo.app.modules.sst.application.dto.AcompanhamentoMetasResponse;
import com.nexo.app.modules.sst.application.dto.MetaInspecaoFilter;
import com.nexo.app.modules.sst.application.dto.MetaInspecaoRequest;
import com.nexo.app.modules.sst.application.dto.MetaInspecaoResponse;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

@Tag(name = "SST - Metas de Inspeção",
        description = "Metas de inspeções por função ou por colaborador, com janelas de apuração recorrentes")
@SecurityRequirement(name = "bearerAuth")
public interface MetaInspecaoControllerDoc {

    @Operation(
            summary = "Cadastrar uma meta de inspeções",
            description = """
                    Define quantas inspeções o alvo deve realizar por janela de apuração. Informe
                    idFuncao (vale para todos os colaboradores ativos da função) OU idColaborador
                    (exceção individual, que prevalece sobre a meta da função).

                    A duração sai de dois campos: a periodicidade (SEMANAL, QUINZENAL, MENSAL,
                    BIMESTRAL, TRIMESTRAL, SEMESTRAL, ANUAL ou PERSONALIZADO) e a data de início da
                    vigência, que ancora a primeira janela. As janelas seguintes se repetem sozinhas
                    até o fim da vigência — que, se omitido, deixa a meta por prazo indeterminado.
                    Em PERSONALIZADO a janela é única e corresponde à própria vigência, por isso a
                    data de fim é obrigatória.""")
    @ApiResponse(responseCode = "201", description = "Meta cadastrada com sucesso")
    @ApiResponse(responseCode = "422", description = "Vigência sobreposta a outra meta ativa do mesmo alvo")
    ResponseEntity<MetaInspecaoResponse> criar(MetaInspecaoRequest request);

    @Operation(summary = "Buscar uma meta por ID")
    ResponseEntity<MetaInspecaoResponse> buscarPorId(UUID id);

    @Operation(summary = "Listar metas com filtros dinâmicos")
    ResponseEntity<PageResponse<MetaInspecaoResponse>> listar(
            @ParameterObject MetaInspecaoFilter filter, Pageable pageable);

    @Operation(
            summary = "Atualizar uma meta",
            description = "Altera quantidade, periodicidade, vigência e descrição. O alvo (função/colaborador) é imutável.")
    ResponseEntity<MetaInspecaoResponse> atualizar(UUID id, MetaInspecaoRequest request);

    @Operation(
            summary = "Acompanhar o cumprimento das metas (indicador)",
            description = """
                    Para cada colaborador alcançado por uma meta vigente na data de referência,
                    devolve a janela apurada, a quantidade exigida, quanto ele já lançou como
                    inspetor nessa janela e a situação (ATINGIDA, EM_ANDAMENTO ou NAO_ATINGIDA),
                    além dos totais consolidados.

                    Filtros: função, colaborador, base operacional, centro de custo, processo de
                    negócio e situação — todos aplicados sobre o colaborador alcançado pela meta e
                    todos com múltipla seleção (repita o parâmetro, `?idFuncao=a&idFuncao=b`, ou
                    separe por vírgula, `?idFuncao=a,b`). Enviar um único valor mantém o
                    comportamento antigo. O centro de custo equivale a selecionar todas as bases
                    vinculadas a ele.

                    Ordenação: `ordenarPor` aceita PERCENTUAL, REALIZADO, META, SALDO,
                    DIAS_RESTANTES, COLABORADOR, FUNCAO, BASE_OPERACIONAL, PROCESSO, CENTRO_CUSTO
                    ou STATUS, e `direcao` aceita ASC ou DESC. O padrão continua sendo PERCENTUAL
                    ASC — quem está mais longe da meta aparece primeiro —, com desempate pelo nome
                    do colaborador. Os totais consolidados consideram apenas os itens que passaram
                    pelos filtros.""")
    ResponseEntity<AcompanhamentoMetasResponse> acompanhar(@ParameterObject AcompanhamentoMetaFilter filter);

    @Operation(summary = "Desativar (soft delete) uma meta")
    ResponseEntity<Void> desativar(UUID id);

    @Operation(summary = "Reativar uma meta")
    ResponseEntity<Void> ativar(UUID id);
}
