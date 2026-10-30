package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.application.dto.FormularioInspecaoFilter;
import com.nexo.app.modules.sst.domain.FormularioInspecao;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Specification para filtros dinâmicos de Formulário de Inspeção.
 */
public class FormularioInspecaoSpecification {

    private FormularioInspecaoSpecification() {}

    public static Specification<FormularioInspecao> filterBy(FormularioInspecaoFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter == null) {
                return cb.conjunction();
            }

            if (StringUtils.hasText(filter.titulo())) {
                predicates.add(cb.like(cb.upper(root.get("titulo")), "%" + filter.titulo().toUpperCase() + "%"));
            }

            if (filter.ativo() != null) {
                predicates.add(cb.equal(root.get("ativo"), filter.ativo()));
            }

            SpecificationUtils.buscaRapida(filter.buscaGlobal(), predicates, cb, root, "titulo", "descricao");

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
