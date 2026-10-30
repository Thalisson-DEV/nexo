package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.application.dto.AgenteResponsavelFilter;
import com.nexo.app.modules.obras.domain.AgenteResponsavel;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class AgenteResponsavelSpecification {

    public static Specification<AgenteResponsavel> filterBy(AgenteResponsavelFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter != null) {
                if (filter.nome() != null && !filter.nome().isBlank()) {
                    predicates.add(cb.like(cb.lower(root.get("nome")), "%" + filter.nome().trim().toLowerCase() + "%"));
                }
                if (filter.email() != null && !filter.email().isBlank()) {
                    predicates.add(cb.like(cb.lower(root.get("email")), "%" + filter.email().trim().toLowerCase() + "%"));
                }
                if (filter.empresaOrgao() != null && !filter.empresaOrgao().isBlank()) {
                    predicates.add(cb.like(cb.lower(root.get("empresaOrgao")), "%" + filter.empresaOrgao().trim().toLowerCase() + "%"));
                }
                if (filter.ativo() != null) {
                    predicates.add(cb.equal(root.get("ativo"), filter.ativo()));
                }
                SpecificationUtils.buscaRapida(filter.buscaGlobal(), predicates, cb, root, "nome", "email", "empresaOrgao");
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
