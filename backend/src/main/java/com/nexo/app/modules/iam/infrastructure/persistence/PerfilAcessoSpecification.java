package com.nexo.app.modules.iam.infrastructure.persistence;

import com.nexo.app.modules.iam.application.dto.PerfilAcessoFilter;
import com.nexo.app.modules.iam.domain.PerfilAcesso;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Filtros dinâmicos para a entidade PerfilAcesso.
 */
public class PerfilAcessoSpecification {

    public static Specification<PerfilAcesso> filterBy(PerfilAcessoFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter == null) {
                return cb.conjunction();
            }

            if (filter.id() != null) {
                predicates.add(cb.equal(root.get("id"), filter.id()));
            }

            if (filter.nome() != null && !filter.nome().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("nome")), "%" + filter.nome().toLowerCase() + "%"));
            }

            if (filter.ativo() != null) {
                predicates.add(cb.equal(root.get("ativo"), filter.ativo()));
            }

            SpecificationUtils.buscaRapida(filter.buscaGlobal(), predicates, cb, root, "nome");

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
