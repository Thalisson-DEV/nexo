package com.nexo.app.modules.estoque.infrastructure.persistence;

import com.nexo.app.modules.estoque.application.dto.MaterialFilter;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class MaterialSpecification {
    public static Specification<Material> filterBy(MaterialFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            if (filter.codigo() != null && !filter.codigo().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("codigo")), "%" + filter.codigo().toLowerCase() + "%"));
            }
            
            if (filter.descricao() != null && !filter.descricao().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("descricao")), "%" + filter.descricao().toLowerCase() + "%"));
            }
            
            if (filter.idProcesso() != null) {
                predicates.add(cb.equal(root.get("idProcesso"), filter.idProcesso()));
            }
            
            if (filter.idAvaliacaoTecnica() != null) {
                predicates.add(cb.equal(root.get("avaliacaoTecnica").get("id"), filter.idAvaliacaoTecnica()));
            }
            
            if (filter.idCentroInvestimento() != null) {
                predicates.add(cb.equal(root.get("centroInvestimento").get("id"), filter.idCentroInvestimento()));
            }
            
            if (filter.detalhaSerie() != null) {
                predicates.add(cb.equal(root.get("detalhaSerie"), filter.detalhaSerie()));
            }

            
            if (filter.ativo() != null) {
                predicates.add(cb.equal(root.get("ativo"), filter.ativo()));
            }
            
            SpecificationUtils.buscaRapida(filter.buscaGlobal(), predicates, cb, root, "codigo", "descricao");

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
