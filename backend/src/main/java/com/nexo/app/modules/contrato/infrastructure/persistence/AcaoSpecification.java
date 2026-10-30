package com.nexo.app.modules.contrato.infrastructure.persistence;

import com.nexo.app.modules.contrato.application.dto.AcaoFilter;
import com.nexo.app.modules.contrato.domain.Acao;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Construtor dinâmico de queries para a entidade Ação.
 * Permite filtrar por codigo, nome e status (ativo).
 */
public class AcaoSpecification {

    public static Specification<Acao> filterBy(AcaoFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.codigo() != null && !filter.codigo().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("codigo")), "%" + filter.codigo().toLowerCase() + "%"));
            }

            if (filter.nome() != null && !filter.nome().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("nome")), "%" + filter.nome().toLowerCase() + "%"));
            }

            if (filter.ativo() != null) {
                predicates.add(cb.equal(root.get("ativo"), filter.ativo()));
            }

            SpecificationUtils.buscaRapida(filter.buscaGlobal(), predicates, cb, root, "codigo", "nome");

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
