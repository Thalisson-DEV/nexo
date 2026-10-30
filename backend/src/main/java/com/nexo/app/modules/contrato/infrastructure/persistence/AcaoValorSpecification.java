package com.nexo.app.modules.contrato.infrastructure.persistence;

import com.nexo.app.modules.contrato.application.dto.AcaoValorFilter;
import com.nexo.app.modules.contrato.domain.AcaoValor;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Construtor dinâmico de queries para a entidade Valor de Ação.
 * Permite filtrar por idAcao, idContrato, dataVigencia e status (ativo).
 */
public class AcaoValorSpecification {

    public static Specification<AcaoValor> filterBy(AcaoValorFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.idAcao() != null) {
                predicates.add(cb.equal(root.get("acao").get("id"), filter.idAcao()));
            }

            if (filter.idContrato() != null) {
                predicates.add(cb.equal(root.get("contrato").get("id"), filter.idContrato()));
            }

            if (filter.dataVigencia() != null) {
                predicates.add(cb.equal(root.get("dataVigencia"), filter.dataVigencia()));
            }

            if (filter.ativo() != null) {
                predicates.add(cb.equal(root.get("ativo"), filter.ativo()));
            }

            // Busca rápida por código/nome da ação ou número do contrato vinculados (joins to-one)
            if (filter.buscaGlobal() != null && !filter.buscaGlobal().isBlank()) {
                Join<AcaoValor, Object> joinAcao = root.join("acao");
                Join<AcaoValor, Object> joinContrato = root.join("contrato");
                SpecificationUtils.buscaRapida(filter.buscaGlobal(), predicates, cb, List.<Expression<String>>of(
                        joinAcao.<String>get("codigo"), joinAcao.<String>get("nome"), joinContrato.<String>get("numeroContrato")));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
