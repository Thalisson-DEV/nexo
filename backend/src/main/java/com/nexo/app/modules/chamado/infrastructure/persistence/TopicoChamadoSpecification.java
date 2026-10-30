package com.nexo.app.modules.chamado.infrastructure.persistence;

import com.nexo.app.modules.chamado.application.dto.TopicoChamadoFilter;
import com.nexo.app.modules.chamado.domain.TopicoChamado;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class TopicoChamadoSpecification {
    private TopicoChamadoSpecification() {}

    public static Specification<TopicoChamado> comFiltros(TopicoChamadoFilter filtro) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filtro != null) {
                List<Expression<String>> busca = List.of(
                        root.get("codigo"), root.get("nome"), root.get("descricao"));
                SpecificationUtils.buscaRapida(filtro.buscaGlobal(), predicates, cb, busca);
                if (filtro.codigo() != null && !filtro.codigo().isBlank()) {
                    predicates.add(cb.equal(cb.upper(root.get("codigo")), filtro.codigo().trim().toUpperCase()));
                }
                if (filtro.equipePadraoId() != null) {
                    predicates.add(cb.equal(root.get("equipePadrao").get("id"), filtro.equipePadraoId()));
                }
                predicates.add(cb.equal(root.get("ativo"), filtro.ativo() == null || filtro.ativo()));
            } else {
                predicates.add(cb.isTrue(root.get("ativo")));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
