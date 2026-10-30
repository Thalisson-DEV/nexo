package com.nexo.app.modules.rh.infrastructure.persistence;

import com.nexo.app.modules.rh.application.dto.CoordenadorFilter;
import com.nexo.app.modules.rh.domain.Colaborador;
import com.nexo.app.modules.rh.domain.Coordenador;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class CoordenadorSpecification {

    public static Specification<Coordenador> filterBy(CoordenadorFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            Join<Coordenador, Colaborador> colaboradorJoin = root.join("colaborador", JoinType.INNER);

            if (filter == null) {
                return cb.conjunction();
            }

            if (filter.idColaborador() != null) {
                predicates.add(cb.equal(colaboradorJoin.get("id"), filter.idColaborador()));
            }

            if (filter.nomeColaborador() != null && !filter.nomeColaborador().isBlank()) {
                String termo = SpecificationUtils.normalizarTermo(filter.nomeColaborador());
                predicates.add(cb.like(colaboradorJoin.get("nomeBusca"), "%" + termo + "%"));
            }

            if (filter.dataInicio() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dataInicio"), filter.dataInicio()));
            }

            if (filter.dataFim() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dataFim"), filter.dataFim()));
            }

            if (filter.ativo() != null) {
                predicates.add(cb.equal(root.get("ativo"), filter.ativo()));
            }

            if (filter.buscaGlobal() != null && !filter.buscaGlobal().isBlank()) {
                SpecificationUtils.buscaRapida(filter.buscaGlobal(), predicates, cb, List.of(colaboradorJoin.get("nomeBusca")));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
