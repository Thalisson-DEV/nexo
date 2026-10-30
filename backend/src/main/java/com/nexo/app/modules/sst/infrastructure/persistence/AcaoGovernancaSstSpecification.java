package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.application.dto.AcaoGovernancaSstFilter;
import com.nexo.app.modules.sst.domain.AcaoGovernancaSst;
import com.nexo.app.modules.sst.domain.StatusAcaoGovernancaSst;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class AcaoGovernancaSstSpecification {
    private AcaoGovernancaSstSpecification() {}

    public static Specification<AcaoGovernancaSst> comFiltros(AcaoGovernancaSstFilter filtro,
                                                               LocalDate hoje) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("ativo")));
            if (filtro == null) return cb.and(predicates.toArray(Predicate[]::new));

            if (filtro.idsCorpos() != null && !filtro.idsCorpos().isEmpty())
                predicates.add(root.get("idCorpo").in(filtro.idsCorpos()));
            if (filtro.idsBases() != null && !filtro.idsBases().isEmpty())
                predicates.add(root.get("idBaseOperacional").in(filtro.idsBases()));
            if (filtro.idResponsavel() != null)
                predicates.add(cb.equal(root.get("idResponsavel"), filtro.idResponsavel()));
            if (filtro.status() != null && !filtro.status().isEmpty())
                predicates.add(root.get("status").in(filtro.status()));
            if (filtro.prioridades() != null && !filtro.prioridades().isEmpty())
                predicates.add(root.get("prioridade").in(filtro.prioridades()));
            if (filtro.tipos() != null && !filtro.tipos().isEmpty())
                predicates.add(root.get("tipo").in(filtro.tipos()));
            if (filtro.prazoInicio() != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("prazo"), filtro.prazoInicio()));
            if (filtro.prazoFim() != null)
                predicates.add(cb.lessThanOrEqualTo(root.get("prazo"), filtro.prazoFim()));
            if (Boolean.TRUE.equals(filtro.vencidas())) {
                predicates.add(cb.lessThan(root.get("prazo"), hoje));
                predicates.add(root.get("status").in(
                        StatusAcaoGovernancaSst.ABERTA, StatusAcaoGovernancaSst.EM_ANDAMENTO));
            }
            SpecificationUtils.buscaRapida(filtro.buscaGlobal(), predicates, cb, root,
                    "titulo", "descricao");
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
