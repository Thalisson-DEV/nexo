package com.nexo.app.modules.desconto.infrastructure.persistence;

import com.nexo.app.modules.desconto.application.dto.MotivoDescontoFilter;
import com.nexo.app.modules.desconto.domain.MotivoDesconto;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class MotivoDescontoSpecification {

    public static Specification<MotivoDesconto> of(MotivoDescontoFilter filtro) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            if (filtro != null) {
                if (filtro.buscaGlobal() != null && !filtro.buscaGlobal().isBlank()) {
                    String termo = "%" + filtro.buscaGlobal().toLowerCase() + "%";
                    predicates.add(cb.like(cb.lower(root.get("descricao")), termo));
                }
                
                if (filtro.ativo() != null) {
                    predicates.add(cb.equal(root.get("ativo"), filtro.ativo()));
                }
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
