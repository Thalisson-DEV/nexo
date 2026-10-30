package com.nexo.app.modules.contrato.infrastructure.persistence;

import com.nexo.app.modules.contrato.application.dto.ContratoFilter;
import com.nexo.app.modules.contrato.domain.Contrato;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Construtor dinâmico de queries para a entidade Contrato.
 * Permite filtrar por numeroContrato, cliente e status (ativo).
 */
public class ContratoSpecification {

    public static Specification<Contrato> filterBy(ContratoFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.numeroContrato() != null && !filter.numeroContrato().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("numeroContrato")), "%" + filter.numeroContrato().toLowerCase() + "%"));
            }

            if (filter.cliente() != null && !filter.cliente().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("cliente")), "%" + filter.cliente().toLowerCase() + "%"));
            }

            if (filter.ativo() != null) {
                predicates.add(cb.equal(root.get("ativo"), filter.ativo()));
            }

            SpecificationUtils.buscaRapida(filter.buscaGlobal(), predicates, cb, root, "numeroContrato", "cliente");

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
