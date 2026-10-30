package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.application.dto.KitConstrutivoFilter;
import com.nexo.app.modules.obras.domain.KitConstrutivo;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class KitConstrutivoSpecification {

    public static Specification<KitConstrutivo> filterBy(KitConstrutivoFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter != null) {
                if (filter.idContrato() != null) {
                    predicates.add(cb.equal(root.get("idContrato"), filter.idContrato()));
                }
                if (filter.grupo() != null && !filter.grupo().isBlank()) {
                    predicates.add(cb.equal(cb.upper(root.get("grupo")), filter.grupo().trim().toUpperCase()));
                }
                if (filter.classeTensao() != null) {
                    // Kit sem classe de tensão vale para todas.
                    predicates.add(cb.or(
                            cb.isMember(filter.classeTensao(), root.get("classesTensao")),
                            cb.isEmpty(root.get("classesTensao"))
                    ));
                }
                predicates.add(cb.equal(root.get("ativo"), filter.ativo() == null || filter.ativo()));
                SpecificationUtils.buscaRapida(filter.buscaGlobal(), predicates, cb, root, "codigo", "descricao");
            } else {
                predicates.add(cb.isTrue(root.get("ativo")));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
