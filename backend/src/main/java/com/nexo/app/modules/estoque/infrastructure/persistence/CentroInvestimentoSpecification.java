package com.nexo.app.modules.estoque.infrastructure.persistence;

import com.nexo.app.modules.estoque.application.dto.CentroInvestimentoFilter;
import com.nexo.app.modules.estoque.domain.CentroInvestimento;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class CentroInvestimentoSpecification {
    public static Specification<CentroInvestimento> filterBy(CentroInvestimentoFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.codigo() != null && !filter.codigo().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("codigo")), "%" + filter.codigo().toLowerCase() + "%"));
            }
            if (filter.ativo() != null) {
                predicates.add(cb.equal(root.get("ativo"), filter.ativo()));
            }
            SpecificationUtils.buscaRapida(filter.buscaGlobal(), predicates, cb, root, "codigo");

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
