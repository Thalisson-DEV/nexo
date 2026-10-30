package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.GravidadeDesvio;
import com.nexo.app.modules.sst.domain.StatusInspecao;
import com.nexo.app.shared.application.SelecaoMultipla;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Filtro dinâmico para listagem de inspeções de segurança.
 *
 * <p>Todos os filtros de recorte aceitam múltipla seleção: repita o parâmetro
 * ({@code ?idProcesso=a&idProcesso=b}) ou separe por vírgula ({@code ?idProcesso=a,b}).
 * Um único valor continua funcionando como antes; omitido, não restringe nada.</p>
 */
public record InspecaoSegurancaFilter(
        List<UUID> idFormulario,
        List<UUID> idTipoInspecao,
        List<UUID> idProcesso,
        List<UUID> idProcessoInspetor,
        List<UUID> idInspetor,
        List<UUID> idVeiculo,
        List<UUID> idEquipe,
        List<UUID> idBaseOperacional,

        @Parameter(description = "Centro de custo (centro de faturamento) das bases operacionais. "
                + "Equivale a selecionar todas as bases vinculadas a ele.")
        List<UUID> idCentroCusto,

        List<UUID> idColaborador,
        List<StatusInspecao> status,
        List<GravidadeDesvio> gravidadeDesvio,
        Boolean apenasComDesvios,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime dataRealizacaoDe,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime dataRealizacaoAte,

        @Parameter(description = "Busca rápida (LIKE, case/accent-insensitive) por observações")
        String buscaGlobal
) {

    public InspecaoSegurancaFilter {
        idFormulario = SelecaoMultipla.normalizar(idFormulario);
        idTipoInspecao = SelecaoMultipla.normalizar(idTipoInspecao);
        idProcesso = SelecaoMultipla.normalizar(idProcesso);
        idProcessoInspetor = SelecaoMultipla.normalizar(idProcessoInspetor);
        idInspetor = SelecaoMultipla.normalizar(idInspetor);
        idVeiculo = SelecaoMultipla.normalizar(idVeiculo);
        idEquipe = SelecaoMultipla.normalizar(idEquipe);
        idBaseOperacional = SelecaoMultipla.normalizar(idBaseOperacional);
        idCentroCusto = SelecaoMultipla.normalizar(idCentroCusto);
        idColaborador = SelecaoMultipla.normalizar(idColaborador);
        status = SelecaoMultipla.normalizar(status);
        gravidadeDesvio = SelecaoMultipla.normalizar(gravidadeDesvio);
    }

    /** Cópia restrita a um inspetor, usada nas visões "minhas inspeções". */
    public InspecaoSegurancaFilter restritoAoInspetor(UUID idInspetor) {
        return new InspecaoSegurancaFilter(idFormulario, idTipoInspecao, idProcesso, idProcessoInspetor, List.of(idInspetor), idVeiculo,
                idEquipe, idBaseOperacional, idCentroCusto, idColaborador, status, gravidadeDesvio,
                apenasComDesvios, dataRealizacaoDe, dataRealizacaoAte, buscaGlobal);
    }

    /** Cópia que devolve apenas inspeções que registraram ao menos um desvio. */
    public InspecaoSegurancaFilter somenteComDesvios() {
        return new InspecaoSegurancaFilter(idFormulario, idTipoInspecao, idProcesso, idProcessoInspetor, idInspetor, idVeiculo,
                idEquipe, idBaseOperacional, idCentroCusto, idColaborador, status, gravidadeDesvio,
                true, dataRealizacaoDe, dataRealizacaoAte, buscaGlobal);
    }

    /** Cópia com outro intervalo de data de realização, preservando os demais recortes. */
    public InspecaoSegurancaFilter noPeriodo(OffsetDateTime de, OffsetDateTime ate) {
        return new InspecaoSegurancaFilter(idFormulario, idTipoInspecao, idProcesso, idProcessoInspetor, idInspetor, idVeiculo,
                idEquipe, idBaseOperacional, idCentroCusto, idColaborador, status, gravidadeDesvio,
                apenasComDesvios, de, ate, buscaGlobal);
    }
}
