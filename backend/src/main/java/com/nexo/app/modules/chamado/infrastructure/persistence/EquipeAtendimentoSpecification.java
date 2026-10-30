package com.nexo.app.modules.chamado.infrastructure.persistence;

import com.nexo.app.modules.chamado.application.dto.EquipeAtendimentoFilter;
import com.nexo.app.modules.chamado.domain.EquipeAtendimento;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class EquipeAtendimentoSpecification {
    private EquipeAtendimentoSpecification() {}

    public static Specification<EquipeAtendimento> comFiltros(EquipeAtendimentoFilter filtro) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filtro != null) {
                List<Expression<String>> busca = List.of(
                        root.get("codigo"), root.get("nome"), root.get("descricao"));
                SpecificationUtils.buscaRapida(filtro.buscaGlobal(), predicates, cb, busca);
                if (filtro.codigo() != null && !filtro.codigo().isBlank()) {
                    predicates.add(cb.equal(cb.upper(root.get("codigo")), filtro.codigo().trim().toUpperCase()));
                }
                predicates.add(cb.equal(root.get("ativo"), filtro.ativo() == null || filtro.ativo()));
            } else {
                predicates.add(cb.isTrue(root.get("ativo")));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
