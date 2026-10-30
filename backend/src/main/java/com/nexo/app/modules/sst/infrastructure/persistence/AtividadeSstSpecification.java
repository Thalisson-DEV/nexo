package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.application.dto.AtividadeSstFilter;
import com.nexo.app.modules.sst.domain.AtividadeSst;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Specification para filtros dinâmicos das atividades de SST.
 */
public class AtividadeSstSpecification {

    private AtividadeSstSpecification() {}

    public static Specification<AtividadeSst> filterBy(AtividadeSstFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter == null) {
                return cb.conjunction();
            }

            if (filter.idGestor() != null) {
                predicates.add(cb.equal(root.get("idGestor"), filter.idGestor()));
            }

            if (filter.idProcesso() != null) {
                predicates.add(cb.equal(root.get("idProcesso"), filter.idProcesso()));
            }

            if (filter.ativo() != null) {
                predicates.add(cb.equal(root.get("ativo"), filter.ativo()));
            }

            SpecificationUtils.buscaRapida(filter.buscaGlobal(), predicates, cb, root, "nome", "descricao");

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
