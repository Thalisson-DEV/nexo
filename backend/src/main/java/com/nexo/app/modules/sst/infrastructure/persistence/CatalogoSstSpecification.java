package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.application.dto.CatalogoSstFilter;
import com.nexo.app.modules.sst.domain.CatalogoSst;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Specification para filtros dinâmicos das entradas de catálogo de SST.
 */
public class CatalogoSstSpecification {

    private CatalogoSstSpecification() {}

    public static Specification<CatalogoSst> filterBy(CatalogoSstFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter == null) {
                return cb.conjunction();
            }

            if (filter.tipo() != null) {
                predicates.add(cb.equal(root.get("tipo"), filter.tipo()));
            }

            if (filter.ativo() != null) {
                predicates.add(cb.equal(root.get("ativo"), filter.ativo()));
            }

            SpecificationUtils.buscaRapida(filter.buscaGlobal(), predicates, cb, root,
                    "nome", "descricao", "codigo");

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
