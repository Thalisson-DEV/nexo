package com.nexo.app.modules.frota.infrastructure.persistence;

import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Filtro comum dos catálogos da frota (tipos de veículo e de implemento). */
public final class CatalogoFrotaSpecification {

    private CatalogoFrotaSpecification() {
    }

    public static <T> Specification<T> of(String busca, Boolean ativo) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            SpecificationUtils.buscaRapida(busca, predicates, cb, root, "nome", "descricao");
            if (ativo != null) {
                predicates.add(cb.equal(root.get("ativo"), ativo));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
