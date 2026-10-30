package com.nexo.app.modules.execucao.infrastructure.persistence;

import com.nexo.app.modules.execucao.application.dto.TipoServicoFilter;
import com.nexo.app.modules.execucao.domain.TipoServico;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TipoServicoSpecification {

    private TipoServicoSpecification() {
    }

    public static Specification<TipoServico> comFiltro(TipoServicoFilter filter) {
        return (root, query, cb) -> {
            if (filter == null) {
                return cb.conjunction();
            }
            List<Predicate> predicates = new ArrayList<>();

            if (filter.ativo() != null) {
                predicates.add(cb.equal(root.get("ativo"), filter.ativo()));
            }
            if (filter.codigo() != null && !filter.codigo().isBlank()) {
                predicates.add(cb.equal(root.get("codigo"), filter.codigo().trim().toUpperCase(Locale.ROOT)));
            }
            if (filter.buscaGlobal() != null && !filter.buscaGlobal().isBlank()) {
                String termo = SpecificationUtils.normalizarTermo(filter.buscaGlobal());
                predicates.add(cb.or(
                        cb.like(root.get("descricaoNormalizada"), "%" + termo + "%"),
                        cb.like(cb.lower(root.get("codigo")), "%" + termo + "%")));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
