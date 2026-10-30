package com.nexo.app.modules.rh.infrastructure.persistence;

import com.nexo.app.modules.rh.application.dto.VeiculoFilter;
import com.nexo.app.modules.rh.domain.Veiculo;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Construtor dinâmico de queries para a entidade Veiculo.
 */
public class VeiculoSpecification {

    public static Specification<Veiculo> filterBy(VeiculoFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.placa() != null && !filter.placa().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("placa")), "%" + filter.placa().toLowerCase() + "%"));
            }

            if (filter.modelo() != null && !filter.modelo().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("modelo")), "%" + filter.modelo().toLowerCase() + "%"));
            }

            if (filter.idBaseOperacional() != null) {
                predicates.add(cb.equal(root.get("baseOperacional").get("id"), filter.idBaseOperacional()));
            }

            if (filter.ativo() != null) {
                predicates.add(cb.equal(root.get("ativo"), filter.ativo()));
            }

            SpecificationUtils.buscaRapida(filter.buscaGlobal(), predicates, cb, root, "placa", "modelo", "fabricante");

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
