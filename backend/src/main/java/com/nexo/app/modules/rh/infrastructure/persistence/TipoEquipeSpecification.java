package com.nexo.app.modules.rh.infrastructure.persistence;

import com.nexo.app.modules.rh.application.dto.TipoEquipeFilter;
import com.nexo.app.modules.rh.domain.TipoEquipe;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Construtor dinâmico de queries para a entidade TipoEquipe.
 */
public class TipoEquipeSpecification {

    public static Specification<TipoEquipe> filterBy(TipoEquipeFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

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
