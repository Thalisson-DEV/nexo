package com.nexo.app.modules.notificacao.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.nexo.app.modules.notificacao.application.dto.RegraNotificacaoFilter;
import com.nexo.app.modules.notificacao.domain.RegraNotificacao;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

public final class RegraNotificacaoSpecification {

    private RegraNotificacaoSpecification() {}

    public static Specification<RegraNotificacao> comFiltros(RegraNotificacaoFilter filtro) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filtro != null) {
                List<Expression<String>> busca = List.of(root.get("nome"), root.get("descricao"));
                SpecificationUtils.buscaRapida(filtro.buscaGlobal(), predicates, cb, busca);

                if (filtro.chaveGatilho() != null && !filtro.chaveGatilho().isBlank()) {
                    predicates.add(cb.equal(root.get("chaveGatilho"), filtro.chaveGatilho().trim()));
                }
                if (filtro.categoria() != null) {
                    predicates.add(cb.equal(root.get("categoria"), filtro.categoria()));
                }
                if (filtro.sistema() != null) {
                    predicates.add(cb.equal(root.get("sistema"), filtro.sistema()));
                }
                predicates.add(cb.equal(root.get("ativo"), filtro.ativo() == null || filtro.ativo()));
            } else {
                predicates.add(cb.isTrue(root.get("ativo")));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
