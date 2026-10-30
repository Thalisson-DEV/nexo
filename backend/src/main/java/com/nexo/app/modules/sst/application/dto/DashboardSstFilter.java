package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.GranularidadeDashboard;
import com.nexo.app.modules.sst.domain.GravidadeDesvio;
import com.nexo.app.modules.sst.domain.StatusDesvio;
import com.nexo.app.shared.application.SelecaoMultipla;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Filtro dos dashboards de SST. Quando {@code inicio}/{@code fim} são omitidos,
 * o período padrão é o mês corrente. Os filtros {@code gravidade}/{@code status}
 * só se aplicam ao dashboard de desvios; {@code granularidade}, às séries dos dois dashboards.
 *
 * <p>Todos os recortes aceitam múltipla seleção: repita o parâmetro
 * ({@code ?idBaseOperacional=a&idBaseOperacional=b}) ou separe por vírgula
 * ({@code ?idBaseOperacional=a,b}). Um único valor continua funcionando como antes.
 * Valores dentro de uma mesma dimensão são combinados com OU; dimensões diferentes,
 * com E.</p>
 */
public record DashboardSstFilter(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime inicio,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime fim,

        List<UUID> idTipoInspecao,
        List<UUID> idProcesso,
        List<UUID> idProcessoInspetor,
        List<UUID> idEquipe,
        List<UUID> idBaseOperacional,

        @Parameter(description = "Centro de custo (centro de faturamento) das bases operacionais. "
                + "Equivale a selecionar todas as bases vinculadas a ele.")
        List<UUID> idCentroCusto,

        List<UUID> idInspetor,
        List<UUID> idFormulario,

        List<GravidadeDesvio> gravidade,
        List<StatusDesvio> status,

        @Parameter(description = "Recorte das séries dos dashboards de inspeções e desvios (DIA, MES ou ANO). "
                + "Omitido, é escolhido pelo tamanho do período.")
        GranularidadeDashboard granularidade
) {

    public DashboardSstFilter {
        idTipoInspecao = SelecaoMultipla.normalizar(idTipoInspecao);
        idProcesso = SelecaoMultipla.normalizar(idProcesso);
        idProcessoInspetor = SelecaoMultipla.normalizar(idProcessoInspetor);
        idEquipe = SelecaoMultipla.normalizar(idEquipe);
        idBaseOperacional = SelecaoMultipla.normalizar(idBaseOperacional);
        idCentroCusto = SelecaoMultipla.normalizar(idCentroCusto);
        idInspetor = SelecaoMultipla.normalizar(idInspetor);
        idFormulario = SelecaoMultipla.normalizar(idFormulario);
        gravidade = SelecaoMultipla.normalizar(gravidade);
        status = SelecaoMultipla.normalizar(status);
    }

    public DashboardSstFilter(
            OffsetDateTime inicio,
            OffsetDateTime fim,
            List<UUID> idTipoInspecao,
            List<UUID> idProcesso,
            List<UUID> idEquipe,
            List<UUID> idBaseOperacional,
            List<UUID> idCentroCusto,
            List<UUID> idInspetor,
            List<UUID> idFormulario,
            List<GravidadeDesvio> gravidade,
            List<StatusDesvio> status,
            GranularidadeDashboard granularidade
    ) {
        this(inicio, fim, idTipoInspecao, idProcesso, null, idEquipe, idBaseOperacional, idCentroCusto, idInspetor,
                idFormulario, gravidade, status, granularidade);
    }

    /** Filtro sem nenhum recorte — usado quando a requisição não envia parâmetros. */
    public static DashboardSstFilter vazio() {
        return new DashboardSstFilter(null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
