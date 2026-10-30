package com.nexo.app.modules.rh.infrastructure.persistence;

import com.nexo.app.modules.rh.application.dto.BaseOperacionalFilter;
import com.nexo.app.modules.rh.domain.BaseOperacional;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Construtor dinâmico de queries para a entidade BaseOperacional.
 * Permite filtrar por nome (LIKE), status e centro de faturamento vinculado.
 */
public class BaseOperacionalSpecification {

    public static Specification<BaseOperacional> filterBy(BaseOperacionalFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.nome() != null && !filter.nome().isBlank()) {
                // Busca parcial case-insensitive no nome
                predicates.add(cb.like(cb.lower(root.get("nome")), "%" + filter.nome().toLowerCase() + "%"));
            }

            if (filter.ativo() != null) {
                predicates.add(cb.equal(root.get("ativo"), filter.ativo()));
            }

            if (filter.idCentroFaturamento() != null) {
                predicates.add(cb.equal(root.get("centroFaturamento").get("id"), filter.idCentroFaturamento()));
            }

            SpecificationUtils.buscaRapida(filter.buscaGlobal(), predicates, cb, root, "nome");

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
