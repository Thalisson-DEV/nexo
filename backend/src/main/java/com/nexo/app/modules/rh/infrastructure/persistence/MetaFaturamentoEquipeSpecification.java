package com.nexo.app.modules.rh.infrastructure.persistence;

import com.nexo.app.modules.rh.application.dto.MetaFaturamentoFilter;
import com.nexo.app.modules.rh.domain.MetaFaturamentoEquipe;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Filtros dinâmicos das metas de faturamento. */
public class MetaFaturamentoEquipeSpecification {

    private MetaFaturamentoEquipeSpecification() {}

    /**
     * @param idsEquipesDoRecorte equipes da base/processo filtrados; {@code null} quando
     *                            o filtro não restringe por base nem por processo
     */
    public static Specification<MetaFaturamentoEquipe> filterBy(MetaFaturamentoFilter filter,
                                                                Collection<UUID> idsEquipesDoRecorte) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (idsEquipesDoRecorte != null) {
                if (idsEquipesDoRecorte.isEmpty()) {
                    return cb.disjunction();
                }
                predicates.add(root.get("idEquipe").in(idsEquipesDoRecorte));
            }

            if (filter == null) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }

            if (filter.idEquipe() != null) {
                predicates.add(cb.equal(root.get("idEquipe"), filter.idEquipe()));
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
