package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.application.dto.ExecucaoChecklistGovernancaFilter;
import com.nexo.app.modules.sst.domain.ExecucaoChecklistGovernancaSst;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class ExecucaoChecklistGovernancaSstSpecification {
    private ExecucaoChecklistGovernancaSstSpecification() {}

    public static Specification<ExecucaoChecklistGovernancaSst> comFiltros(
            ExecucaoChecklistGovernancaFilter filtro) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("ativo")));
            if (filtro == null) return cb.and(predicates.toArray(Predicate[]::new));
            if (filtro.idsCorpos() != null && !filtro.idsCorpos().isEmpty())
                predicates.add(root.get("idCorpo").in(filtro.idsCorpos()));
            if (filtro.idsBases() != null && !filtro.idsBases().isEmpty())
                predicates.add(root.get("idBaseOperacional").in(filtro.idsBases()));
            if (filtro.idModelo() != null)
                predicates.add(cb.equal(root.get("idModelo"), filtro.idModelo()));
            if (filtro.idExecutor() != null)
                predicates.add(cb.equal(root.get("idExecutor"), filtro.idExecutor()));
            if (filtro.status() != null && !filtro.status().isEmpty())
                predicates.add(root.get("status").in(filtro.status()));
            if (filtro.dataInicio() != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("dataExecucao"), filtro.dataInicio()));
            if (filtro.dataFim() != null)
                predicates.add(cb.lessThanOrEqualTo(root.get("dataExecucao"), filtro.dataFim()));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
