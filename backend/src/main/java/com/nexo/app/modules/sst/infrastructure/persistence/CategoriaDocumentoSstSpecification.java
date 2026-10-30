package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.application.dto.CategoriaDocumentoSstFilter;
import com.nexo.app.modules.sst.domain.CategoriaDocumentoSst;
import com.nexo.app.shared.application.SelecaoMultipla;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class CategoriaDocumentoSstSpecification {

    private CategoriaDocumentoSstSpecification() {
    }

    public static Specification<CategoriaDocumentoSst> comFiltros(CategoriaDocumentoSstFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter == null) {
                predicates.add(cb.isTrue(root.get("ativo")));
            } else {
                if (filter.ativa() == null) {
                    predicates.add(cb.isTrue(root.get("ativo")));
                } else {
                    predicates.add(cb.equal(root.get("ativo"), filter.ativa()));
                }
                if (!SelecaoMultipla.vazia(filter.contexto())) {
                    predicates.add(root.get("contexto").in(filter.contexto()));
                }
                if (!SelecaoMultipla.vazia(filter.natureza())) {
                    predicates.add(root.get("natureza").in(filter.natureza()));
                }
                if (!SelecaoMultipla.vazia(filter.escopo())) {
                    predicates.add(root.get("escopoTitular").in(filter.escopo()));
                }
                if (filter.confirmada() != null) {
                    predicates.add(cb.equal(root.get("confirmada"), filter.confirmada()));
                }
                if (filter.jurisdicao() != null && !filter.jurisdicao().isBlank()) {
                    predicates.add(cb.equal(cb.lower(root.get("jurisdicao")),
                            filter.jurisdicao().trim().toLowerCase()));
                }
                SpecificationUtils.buscaRapida(filter.buscaGlobal(), predicates, cb,
                        List.<Expression<String>>of(root.get("codigo"), root.get("nome"),
                                root.get("descricao"), root.get("autoridadeEmissora"),
                                root.get("identificadorFonte")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
