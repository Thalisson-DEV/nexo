package com.nexo.app.modules.estoque.infrastructure.persistence;

import com.nexo.app.modules.estoque.application.dto.SaldoFilter;
import com.nexo.app.modules.estoque.domain.Saldo;
import com.nexo.app.modules.estoque.domain.SaldoSerie;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SaldoSpecification {
    public static Specification<Saldo> filterBy(SaldoFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            if (filter.idEstoque() != null) {
                predicates.add(cb.equal(root.get("estoque").get("id"), filter.idEstoque()));
            }

            if (filter.idEquipe() != null) {
                predicates.add(cb.equal(root.get("estoque").get("idEquipe"), filter.idEquipe()));
            }
            
            if (filter.idMaterial() != null) {
                predicates.add(cb.equal(root.get("material").get("id"), filter.idMaterial()));
            }

            if (filter.condicao() != null) {
                predicates.add(cb.equal(root.get("condicao"), filter.condicao()));
            }

            if (filter.numSerie() != null && !filter.numSerie().isBlank()) {
                Subquery<UUID> subquery = query.subquery(UUID.class);
                var subRoot = subquery.from(SaldoSerie.class);
                subquery.select(subRoot.get("material").get("id"))
                        .where(
                                cb.equal(cb.lower(subRoot.get("numSerie")), filter.numSerie().toLowerCase()),
                                cb.equal(subRoot.get("estoque"), root.get("estoque")),
                                cb.equal(subRoot.get("material"), root.get("material")),
                                cb.equal(subRoot.get("condicao"), root.get("condicao")),
                                cb.isTrue(subRoot.get("ativo"))
                        );
                predicates.add(cb.exists(subquery));
            }

            if (filter.buscaGlobal() != null && !filter.buscaGlobal().isBlank()) {
                SpecificationUtils.buscaRapida(filter.buscaGlobal(), predicates, cb, List.<Expression<String>>of(
                    root.get("material").<String>get("codigo"), root.get("material").<String>get("descricao")));
            }
            
            if (filter.quantidadeMinima() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("quantidade"), filter.quantidadeMinima()));
            }
            
            if (filter.quantidadeMaxima() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("quantidade"), filter.quantidadeMaxima()));
            }

            if (filter.negativo() != null) {
                predicates.add(Boolean.TRUE.equals(filter.negativo())
                        ? cb.lessThan(root.get("quantidade"), java.math.BigDecimal.ZERO)
                        : cb.greaterThanOrEqualTo(root.get("quantidade"), java.math.BigDecimal.ZERO));
            }
            
            if (filter.ativo() != null) {
                predicates.add(cb.equal(root.get("ativo"), filter.ativo()));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
