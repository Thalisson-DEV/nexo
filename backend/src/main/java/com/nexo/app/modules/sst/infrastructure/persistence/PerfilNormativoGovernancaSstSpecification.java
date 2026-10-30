package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.application.dto.PerfilNormativoGovernancaSstFilter;
import com.nexo.app.modules.sst.domain.PerfilNormativoGovernancaSst;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class PerfilNormativoGovernancaSstSpecification {

    private PerfilNormativoGovernancaSstSpecification() {
    }

    public static Specification<PerfilNormativoGovernancaSst> filterBy(
            PerfilNormativoGovernancaSstFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("ativo")));
            if (filter == null) {
                return cb.and(predicates.toArray(Predicate[]::new));
            }
            if (filter.tipoCorpo() != null) {
                predicates.add(cb.equal(root.get("tipoCorpo"), filter.tipoCorpo()));
            }
            if (filter.escopo() != null) {
                predicates.add(cb.equal(root.get("escopo"), filter.escopo()));
            }
            if (filter.jurisdicao() != null && !filter.jurisdicao().isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("jurisdicao")),
                        filter.jurisdicao().trim().toUpperCase()));
            }
            if (filter.confirmado() != null) {
                predicates.add(cb.equal(root.get("confirmado"), filter.confirmado()));
            }
            if (filter.vigenteEm() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("inicioVigencia"), filter.vigenteEm()));
                predicates.add(cb.or(cb.isNull(root.get("fimVigencia")),
                        cb.greaterThanOrEqualTo(root.get("fimVigencia"), filter.vigenteEm())));
            }
            SpecificationUtils.buscaRapida(filter.buscaGlobal(), predicates, cb, root,
                    "codigo", "orgaoEmissor", "atoNormativo", "edicao");
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
