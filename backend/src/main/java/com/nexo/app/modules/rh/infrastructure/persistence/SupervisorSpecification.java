package com.nexo.app.modules.rh.infrastructure.persistence;

import com.nexo.app.modules.rh.application.dto.SupervisorFilter;
import com.nexo.app.modules.rh.domain.Colaborador;
import com.nexo.app.modules.rh.domain.Coordenador;
import com.nexo.app.modules.rh.domain.Supervisor;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class SupervisorSpecification {

    public static Specification<Supervisor> filterBy(SupervisorFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            Join<Supervisor, Colaborador> colaboradorJoin = root.join("colaborador", JoinType.INNER);
            Join<Supervisor, Coordenador> coordenadorJoin = root.join("coordenador", JoinType.INNER);
            Join<Coordenador, Colaborador> coordenadorColaboradorJoin = coordenadorJoin.join("colaborador", JoinType.INNER);

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

            if (filter.idCoordenador() != null) {
                predicates.add(cb.equal(coordenadorJoin.get("id"), filter.idCoordenador()));
            }

            if (filter.nomeCoordenador() != null && !filter.nomeCoordenador().isBlank()) {
                String termo = SpecificationUtils.normalizarTermo(filter.nomeCoordenador());
                predicates.add(cb.like(coordenadorColaboradorJoin.get("nomeBusca"), "%" + termo + "%"));
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
                SpecificationUtils.buscaRapida(
                        filter.buscaGlobal(),
                        predicates,
                        cb,
                        List.of(colaboradorJoin.get("nomeBusca"), coordenadorColaboradorJoin.get("nomeBusca"))
                );
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
