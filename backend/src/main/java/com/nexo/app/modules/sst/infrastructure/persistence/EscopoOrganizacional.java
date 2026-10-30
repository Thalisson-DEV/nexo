package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.rh.domain.BaseOperacional;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import java.util.Collection;
import java.util.UUID;

/**
 * Traduz o recorte por centro de custo — uma dimensão que vive no RH — para as bases
 * operacionais correspondentes.
 *
 * <p>As inspeções guardam apenas o id da base operacional; não existe coluna de centro
 * de custo a ser comparada. Filtrar por centro de custo é, portanto, filtrar pelas bases
 * vinculadas a ele, o que aqui é resolvido por subquery em {@link BaseOperacional} —
 * mesmo padrão já usado nesta camada para Veículo, Equipe e Colaborador.</p>
 */
final class EscopoOrganizacional {

    private EscopoOrganizacional() {
    }

    /** Ids das bases operacionais vinculadas aos centros de custo informados. */
    static Subquery<UUID> basesDosCentrosCusto(CriteriaQuery<?> query, CriteriaBuilder cb,
                                               Collection<UUID> idsCentroCusto) {
        Subquery<UUID> subquery = query.subquery(UUID.class);
        Root<BaseOperacional> base = subquery.from(BaseOperacional.class);
        return subquery.select(base.get("id"))
                .where(base.get("centroFaturamento").get("id").in(idsCentroCusto));
    }
}
