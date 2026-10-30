package com.nexo.app.modules.frota.infrastructure.persistence;

import com.nexo.app.modules.frota.application.dto.DocumentoFrotaFilter;
import com.nexo.app.modules.frota.domain.DocumentoFrota;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class DocumentoFrotaSpecification {

    private DocumentoFrotaSpecification() {
    }

    /**
     * @param idsVeiculoPorEscopo    veículos das bases filtradas, ou nulo sem filtro de base
     * @param idsImplementoPorEscopo implementos das bases filtradas, ou nulo sem filtro de base
     */
    public static Specification<DocumentoFrota> of(DocumentoFrotaFilter filtro,
                                                   Collection<UUID> idsVeiculoPorEscopo,
                                                   Collection<UUID> idsImplementoPorEscopo,
                                                   LocalDate hoje, int diasAviso) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("ativo")));
            if (filtro == null) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }
            SpecificationUtils.buscaRapida(filtro.busca(), predicates, cb, root, "numero", "descricao");
            if (filtro.idVeiculo() != null) {
                predicates.add(cb.equal(root.get("idVeiculo"), filtro.idVeiculo()));
            }
            if (filtro.idImplemento() != null) {
                predicates.add(cb.equal(root.get("idImplemento"), filtro.idImplemento()));
            }
            if (idsVeiculoPorEscopo != null || idsImplementoPorEscopo != null) {
                List<Predicate> escopo = new ArrayList<>();
                if (idsVeiculoPorEscopo != null && !idsVeiculoPorEscopo.isEmpty()) {
                    escopo.add(root.get("idVeiculo").in(idsVeiculoPorEscopo));
                }
                if (idsImplementoPorEscopo != null && !idsImplementoPorEscopo.isEmpty()) {
                    escopo.add(root.get("idImplemento").in(idsImplementoPorEscopo));
                }
                predicates.add(escopo.isEmpty() ? cb.disjunction() : cb.or(escopo.toArray(new Predicate[0])));
            }
            SpecificationUtils.filtrarPor(filtro.tipos(), predicates, root.get("tipo"));
            if (filtro.situacao() != null) {
                var vencimento = root.<LocalDate>get("dataVencimento");
                switch (filtro.situacao()) {
                    case VENCIDO -> predicates.add(cb.lessThan(vencimento, hoje));
                    case A_VENCER -> predicates.add(cb.between(vencimento, hoje, hoje.plusDays(diasAviso)));
                    case VIGENTE -> predicates.add(cb.greaterThan(vencimento, hoje.plusDays(diasAviso)));
                    case SEM_VENCIMENTO -> predicates.add(cb.isNull(vencimento));
                }
            }
            if (filtro.vencimentoAte() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dataVencimento"), filtro.vencimentoAte()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
