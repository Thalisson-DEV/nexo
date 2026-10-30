package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.application.dto.CorpoGovernancaSstFilter;
import com.nexo.app.modules.sst.domain.CorpoGovernancaSst;
import com.nexo.app.shared.application.SelecaoMultipla;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class CorpoGovernancaSstSpecification {

    private CorpoGovernancaSstSpecification() {
    }

    public static Specification<CorpoGovernancaSst> filterBy(CorpoGovernancaSstFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("ativo")));
            if (filter == null) {
                return cb.and(predicates.toArray(Predicate[]::new));
            }
            if (!SelecaoMultipla.vazia(filter.tipo())) {
                predicates.add(root.get("tipo").in(filter.tipo()));
            }
            if (!SelecaoMultipla.vazia(filter.status())) {
                predicates.add(root.get("status").in(filter.status()));
            }
            if (!SelecaoMultipla.vazia(filter.regimeComposicao())) {
                predicates.add(root.get("regimeComposicao").in(filter.regimeComposicao()));
            }
            if (!SelecaoMultipla.vazia(filter.idBaseOperacional())) {
                predicates.add(root.get("idBaseOperacional").in(filter.idBaseOperacional()));
            }
            if (filter.idPerfilNormativo() != null) {
                predicates.add(cb.equal(root.get("idPerfilNormativo"), filter.idPerfilNormativo()));
            }
            if (filter.conformidadeNormativaValidada() != null) {
                predicates.add(cb.equal(root.get("conformidadeNormativaValidada"),
                        filter.conformidadeNormativaValidada()));
            }
            if (filter.periodoDe() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("fimPeriodo"), filter.periodoDe()));
            }
            if (filter.periodoAte() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("inicioPeriodo"), filter.periodoAte()));
            }
            SpecificationUtils.buscaRapida(filter.buscaGlobal(), predicates, cb, root,
                    "nome", "unidadeGovernanca", "baseNome");
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
