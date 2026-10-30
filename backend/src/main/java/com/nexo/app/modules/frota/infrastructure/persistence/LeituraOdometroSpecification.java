package com.nexo.app.modules.frota.infrastructure.persistence;

import com.nexo.app.modules.frota.application.dto.LeituraOdometroFilter;
import com.nexo.app.modules.frota.domain.LeituraOdometro;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class LeituraOdometroSpecification {

    private LeituraOdometroSpecification() {
    }

    /** @param idsVeiculoPorEscopo veículos das bases filtradas, ou nulo sem esse filtro */
    public static Specification<LeituraOdometro> of(LeituraOdometroFilter filtro, Collection<UUID> idsVeiculoPorEscopo) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filtro == null) {
                return cb.conjunction();
            }
            if (filtro.idVeiculo() != null) {
                predicates.add(cb.equal(root.get("idVeiculo"), filtro.idVeiculo()));
            }
            if (idsVeiculoPorEscopo != null) {
                predicates.add(idsVeiculoPorEscopo.isEmpty()
                        ? cb.disjunction() : root.get("idVeiculo").in(idsVeiculoPorEscopo));
            }
            SpecificationUtils.filtrarPor(filtro.situacoes(), predicates, root.get("situacao"));
            SpecificationUtils.filtrarPor(filtro.origens(), predicates, root.get("origem"));
            if (filtro.de() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("lidoEm"), filtro.de()));
            }
            if (filtro.ate() != null) {
                predicates.add(cb.lessThan(root.get("lidoEm"), filtro.ate()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
