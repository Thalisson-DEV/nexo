package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.application.dto.MetaInspecaoFilter;
import com.nexo.app.modules.sst.domain.MetaInspecao;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Specification para filtros dinâmicos de Meta de Inspeção.
 */
public class MetaInspecaoSpecification {

    private MetaInspecaoSpecification() {}

    public static Specification<MetaInspecao> filterBy(MetaInspecaoFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter == null) {
                return cb.conjunction();
            }

            if (filter.idTipoInspecao() != null) {
                predicates.add(cb.equal(root.get("idTipoInspecao"), filter.idTipoInspecao()));
            }

            if (filter.escopo() != null) {
                predicates.add(cb.equal(root.get("escopo"), filter.escopo()));
            }

            if (filter.idFuncao() != null) {
                predicates.add(cb.equal(root.get("idFuncao"), filter.idFuncao()));
            }

            if (filter.idColaborador() != null) {
                predicates.add(cb.equal(root.get("idColaborador"), filter.idColaborador()));
            }

            if (filter.periodicidade() != null) {
                predicates.add(cb.equal(root.get("periodicidade"), filter.periodicidade()));
            }

            if (filter.vigenteEm() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("vigenciaInicio"), filter.vigenteEm()));
                predicates.add(cb.or(
                        cb.isNull(root.get("vigenciaFim")),
                        cb.greaterThanOrEqualTo(root.get("vigenciaFim"), filter.vigenteEm())));
            }

            if (filter.ativo() != null) {
                predicates.add(cb.equal(root.get("ativo"), filter.ativo()));
            }

            SpecificationUtils.buscaRapida(filter.buscaGlobal(), predicates, cb, root, "descricao");

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
