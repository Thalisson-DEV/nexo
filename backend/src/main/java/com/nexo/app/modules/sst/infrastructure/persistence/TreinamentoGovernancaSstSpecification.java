package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.application.dto.TreinamentoGovernancaSstFilter;
import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;
import com.nexo.app.modules.sst.domain.TreinamentoGovernancaSst;
import com.nexo.app.shared.application.SelecaoMultipla;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class TreinamentoGovernancaSstSpecification {

    private TreinamentoGovernancaSstSpecification() {
    }

    public static Specification<TreinamentoGovernancaSst> comFiltros(
            TreinamentoGovernancaSstFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter == null || filter.ativo() == null) {
                predicates.add(cb.isTrue(root.get("ativo")));
            } else {
                predicates.add(cb.equal(root.get("ativo"), filter.ativo()));
            }
            if (filter != null) {
                if (!SelecaoMultipla.vazia(filter.contexto())) {
                    List<Predicate> contextos = new ArrayList<>();
                    if (filter.contexto().contains(TipoCorpoGovernancaSst.CIPA)) {
                        contextos.add(cb.isTrue(root.get("aplicaCipa")));
                    }
                    if (filter.contexto().contains(TipoCorpoGovernancaSst.BRIGADA)) {
                        contextos.add(cb.isTrue(root.get("aplicaBrigada")));
                    }
                    predicates.add(cb.or(contextos.toArray(Predicate[]::new)));
                }
                SpecificationUtils.buscaRapida(filter.buscaGlobal(), predicates, cb,
                        List.<Expression<String>>of(root.get("codigo"), root.get("nome"),
                                root.get("descricao")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
