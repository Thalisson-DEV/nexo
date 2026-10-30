package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.application.dto.StatusObraFilter;
import com.nexo.app.modules.obras.domain.StatusObra;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class StatusObraSpecification {

    public static Specification<StatusObra> filterBy(StatusObraFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter != null) {
                if (filter.nome() != null && !filter.nome().isBlank()) {
                    predicates.add(cb.like(cb.lower(root.get("nome")), "%" + filter.nome().trim().toLowerCase() + "%"));
                }
                if (filter.concluida() != null) {
                    predicates.add(cb.equal(root.get("concluida"), filter.concluida()));
                }
                if (filter.ativo() != null) {
                    predicates.add(cb.equal(root.get("ativo"), filter.ativo()));
                }
                SpecificationUtils.buscaRapida(filter.buscaGlobal(), predicates, cb, root, "nome");
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
