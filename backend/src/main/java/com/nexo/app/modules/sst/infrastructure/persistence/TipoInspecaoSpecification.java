package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.application.dto.TipoInspecaoFilter;
import com.nexo.app.modules.sst.domain.TipoInspecao;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class TipoInspecaoSpecification {

    private TipoInspecaoSpecification() {
    }

    public static Specification<TipoInspecao> comFiltro(TipoInspecaoFilter filter) {
        return (root, query, cb) -> {
            if (filter == null) {
                return cb.conjunction();
            }

            List<Predicate> predicates = new ArrayList<>();

            if (filter.ativo() != null) {
                predicates.add(cb.equal(root.get("ativo"), filter.ativo()));
            }

            if (filter.buscaGlobal() != null && !filter.buscaGlobal().isBlank()) {
                String termo = SpecificationUtils.normalizarTermo(filter.buscaGlobal());
                predicates.add(cb.like(root.get("nomeNormalizado"), "%" + termo + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
