package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.application.dto.ModeloChecklistGovernancaFilter;
import com.nexo.app.modules.sst.domain.ModeloChecklistGovernancaSst;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class ModeloChecklistGovernancaSstSpecification {
    private ModeloChecklistGovernancaSstSpecification() {}

    public static Specification<ModeloChecklistGovernancaSst> comFiltros(
            ModeloChecklistGovernancaFilter filtro) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filtro == null) return cb.conjunction();
            if (filtro.tiposCorpo() != null && !filtro.tiposCorpo().isEmpty())
                predicates.add(root.get("tipoCorpo").in(filtro.tiposCorpo()));
            if (filtro.ativo() != null)
                predicates.add(cb.equal(root.get("ativo"), filtro.ativo()));
            SpecificationUtils.buscaRapida(filtro.buscaGlobal(), predicates, cb, root,
                    "nome", "orientacao", "referenciaNormativa");
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
