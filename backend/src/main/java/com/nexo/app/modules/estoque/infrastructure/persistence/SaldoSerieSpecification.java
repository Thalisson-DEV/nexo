package com.nexo.app.modules.estoque.infrastructure.persistence;

import com.nexo.app.modules.estoque.application.dto.SaldoSerieFilter;
import com.nexo.app.modules.estoque.domain.SaldoSerie;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class SaldoSerieSpecification {
    public static Specification<SaldoSerie> filterBy(SaldoSerieFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            if (filter.idEstoque() != null) {
                predicates.add(cb.equal(root.get("estoque").get("id"), filter.idEstoque()));
            }

            if (filter.idEquipe() != null) {
                predicates.add(cb.equal(root.get("estoque").get("idEquipe"), filter.idEquipe()));
            }
            
            if (filter.idMaterial() != null) {
                predicates.add(cb.equal(root.get("material").get("id"), filter.idMaterial()));
            }

            if (filter.condicao() != null) {
                predicates.add(cb.equal(root.get("condicao"), filter.condicao()));
            }

            if (filter.numSerie() != null && !filter.numSerie().isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("numSerie")), filter.numSerie().toLowerCase()));
            }

            if (filter.buscaGlobal() != null && !filter.buscaGlobal().isBlank()) {
                SpecificationUtils.buscaRapida(filter.buscaGlobal(), predicates, cb, List.<Expression<String>>of(
                    root.get("material").<String>get("codigo"), root.get("material").<String>get("descricao"), root.<String>get("numSerie")));
            }
            
            if (filter.ativo() != null) {
                predicates.add(cb.equal(root.get("ativo"), filter.ativo()));
            } else {
                predicates.add(cb.isTrue(root.get("ativo")));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
