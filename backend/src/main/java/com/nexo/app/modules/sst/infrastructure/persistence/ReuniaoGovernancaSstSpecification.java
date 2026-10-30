package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.application.dto.ReuniaoGovernancaDto;
import com.nexo.app.modules.sst.domain.ReuniaoGovernancaSst;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class ReuniaoGovernancaSstSpecification {
    private ReuniaoGovernancaSstSpecification() {}

    public static Specification<ReuniaoGovernancaSst> comFiltros(ReuniaoGovernancaDto.Filter filtro) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.isTrue(root.get("ativo")));
            if (filtro == null) return cb.and(p.toArray(Predicate[]::new));
            if (filtro.idsBases() != null && !filtro.idsBases().isEmpty())
                p.add(root.get("idBaseOperacional").in(filtro.idsBases()));
            if (filtro.idsCorpos() != null && !filtro.idsCorpos().isEmpty())
                p.add(root.get("idCorpo").in(filtro.idsCorpos()));
            if (filtro.tiposCorpo() != null && !filtro.tiposCorpo().isEmpty())
                p.add(root.get("tipoCorpo").in(filtro.tiposCorpo()));
            if (filtro.tipos() != null && !filtro.tipos().isEmpty())
                p.add(root.get("tipo").in(filtro.tipos()));
            if (filtro.status() != null && !filtro.status().isEmpty())
                p.add(root.get("status").in(filtro.status()));
            if (filtro.dataInicio() != null)
                p.add(cb.greaterThanOrEqualTo(root.get("dataHora"), filtro.dataInicio()));
            if (filtro.dataFim() != null)
                p.add(cb.lessThanOrEqualTo(root.get("dataHora"), filtro.dataFim()));
            SpecificationUtils.buscaRapida(filtro.buscaGlobal(), p, cb, root, "localOuMeio");
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
