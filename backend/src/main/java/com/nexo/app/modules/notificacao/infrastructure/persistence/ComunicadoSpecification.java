package com.nexo.app.modules.notificacao.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.nexo.app.modules.notificacao.application.dto.ComunicadoFilter;
import com.nexo.app.modules.notificacao.domain.Comunicado;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

public final class ComunicadoSpecification {

    private ComunicadoSpecification() {}

    public static Specification<Comunicado> comFiltros(ComunicadoFilter filtro) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filtro != null) {
                List<Expression<String>> busca = List.of(root.get("titulo"), root.get("resumo"));
                SpecificationUtils.buscaRapida(filtro.buscaGlobal(), predicates, cb, busca);

                if (filtro.tipo() != null) {
                    predicates.add(cb.equal(root.get("tipo"), filtro.tipo()));
                }
                if (filtro.status() != null) {
                    predicates.add(cb.equal(root.get("status"), filtro.status()));
                }
                if (filtro.exigeConfirmacao() != null) {
                    predicates.add(cb.equal(root.get("exigeConfirmacao"), filtro.exigeConfirmacao()));
                }
                predicates.add(cb.equal(root.get("ativo"), filtro.ativo() == null || filtro.ativo()));
            } else {
                predicates.add(cb.isTrue(root.get("ativo")));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
