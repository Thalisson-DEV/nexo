package com.nexo.app.modules.financeiro.infrastructure.persistence;

import com.nexo.app.modules.financeiro.application.dto.FormularioFilter;
import com.nexo.app.modules.financeiro.domain.FormularioSolicitacao;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Filtros da listagem de variantes de formulário. */
public final class FormularioSolicitacaoSpecification {

    private FormularioSolicitacaoSpecification() {
    }

    public static Specification<FormularioSolicitacao> comFiltros(FormularioFilter filtro) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filtro != null) {
                SpecificationUtils.buscaRapida(filtro.buscaGlobal(), predicates, cb, root,
                        "codigo", "titulo", "descricao");

                if (filtro.tipoSolicitacao() != null) {
                    predicates.add(cb.equal(root.get("tipoSolicitacao"), filtro.tipoSolicitacao()));
                }
                if (filtro.ativo() != null) {
                    predicates.add(cb.equal(root.get("ativo"), filtro.ativo()));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
