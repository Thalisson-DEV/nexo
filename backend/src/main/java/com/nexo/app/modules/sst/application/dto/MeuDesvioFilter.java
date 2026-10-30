package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.GravidadeDesvio;
import com.nexo.app.modules.sst.domain.StatusDesvio;
import com.nexo.app.shared.application.SelecaoMultipla;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Filtro dos desvios atribuídos ao inspetor autenticado. Os recortes aceitam
 * múltipla seleção (parâmetro repetido ou valores separados por vírgula).
 */
public record MeuDesvioFilter(
        List<StatusDesvio> status,
        Boolean apenasPendentes,
        List<GravidadeDesvio> gravidade,
        Boolean apenasVencidos,
        List<UUID> idProcesso,
        List<UUID> idProcessoInspetor,
        List<UUID> idBaseOperacional,

        @Parameter(description = "Centro de custo (centro de faturamento) das bases operacionais. "
                + "Equivale a selecionar todas as bases vinculadas a ele.")
        List<UUID> idCentroCusto,

        List<UUID> idEquipe,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime dataInspecaoDe,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime dataInspecaoAte,

        @Parameter(description = "Busca por descrição ou ação corretiva")
        String buscaGlobal
) {

    public MeuDesvioFilter {
        status = SelecaoMultipla.normalizar(status);
        gravidade = SelecaoMultipla.normalizar(gravidade);
        idProcesso = SelecaoMultipla.normalizar(idProcesso);
        idProcessoInspetor = SelecaoMultipla.normalizar(idProcessoInspetor);
        idBaseOperacional = SelecaoMultipla.normalizar(idBaseOperacional);
        idCentroCusto = SelecaoMultipla.normalizar(idCentroCusto);
        idEquipe = SelecaoMultipla.normalizar(idEquipe);
    }

    public MeuDesvioFilter(
            List<StatusDesvio> status,
            Boolean apenasPendentes,
            List<GravidadeDesvio> gravidade,
            Boolean apenasVencidos,
            List<UUID> idProcesso,
            List<UUID> idBaseOperacional,
            List<UUID> idCentroCusto,
            List<UUID> idEquipe,
            OffsetDateTime dataInspecaoDe,
            OffsetDateTime dataInspecaoAte,
            String buscaGlobal
    ) {
        this(status, apenasPendentes, gravidade, apenasVencidos, idProcesso, null, idBaseOperacional, idCentroCusto,
                idEquipe, dataInspecaoDe, dataInspecaoAte, buscaGlobal);
    }

    public static MeuDesvioFilter vazio() {
        return new MeuDesvioFilter(null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
