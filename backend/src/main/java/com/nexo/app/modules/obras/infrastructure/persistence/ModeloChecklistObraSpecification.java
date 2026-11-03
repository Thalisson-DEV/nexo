package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.application.dto.ModeloChecklistObraFilter;
import com.nexo.app.modules.obras.domain.ModeloChecklistObra;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class ModeloChecklistObraSpecification {

    public static Specification<ModeloChecklistObra> filterBy(ModeloChecklistObraFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.finalidade() != null) {
                predicates.add(cb.equal(root.get("finalidade"), filter.finalidade()));
            }
            predicates.add(cb.equal(root.get("ativo"), filter.ativo() == null || filter.ativo()));
            SpecificationUtils.buscaRapida(filter.buscaGlobal(), predicates, cb, root, "titulo", "descricao");
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
