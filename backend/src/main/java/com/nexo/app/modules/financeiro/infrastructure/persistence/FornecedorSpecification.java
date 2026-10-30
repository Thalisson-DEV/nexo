package com.nexo.app.modules.financeiro.infrastructure.persistence;

import com.nexo.app.modules.financeiro.application.dto.FornecedorFilter;
import com.nexo.app.modules.financeiro.domain.Fornecedor;
import com.nexo.app.modules.financeiro.domain.StatusFornecedor;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Filtros da listagem de fornecedores.
 *
 * <p>A busca rápida cobre nome fantasia, razão social e documento — todos em texto
 * puro. Os dados bancários ficam de fora por serem cifrados em repouso, o que
 * impede comparação por {@code LIKE}.</p>
 */
public final class FornecedorSpecification {

    private FornecedorSpecification() {
    }

    public static Specification<Fornecedor> comFiltros(FornecedorFilter filtro) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filtro != null) {
                SpecificationUtils.buscaRapida(filtro.buscaGlobal(), predicates, cb, root,
                        "nomeFantasia", "razaoSocial", "documento");

                if (filtro.status() != null) {
                    predicates.add(cb.equal(root.get("status"), filtro.status()));
                }
                if (Boolean.TRUE.equals(filtro.apenasUtilizaveis())) {
                    predicates.add(cb.isTrue(root.get("ativo")));
                    predicates.add(root.get("status").in(StatusFornecedor.PENDENTE, StatusFornecedor.HOMOLOGADO));
                }
                if (filtro.ativo() != null) {
                    predicates.add(cb.equal(root.get("ativo"), filtro.ativo()));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
