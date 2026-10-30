package com.nexo.app.modules.frota.infrastructure.persistence;

import com.nexo.app.modules.frota.application.dto.CondutorFilter;
import com.nexo.app.modules.frota.domain.Condutor;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import com.nexo.app.shared.infrastructure.security.HashUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class CondutorSpecification {

    private CondutorSpecification() {
    }

    /**
     * @param idsColaboradorPorNome colaboradores cujo nome casa com a busca (resolvido no RH)
     * @param idsColaboradorPorBase colaboradores das bases filtradas (resolvido no RH), ou nulo sem filtro
     */
    public static Specification<Condutor> of(CondutorFilter filtro, Collection<UUID> idsColaboradorPorNome,
                                             Collection<UUID> idsColaboradorPorBase,
                                             LocalDate hoje, int diasAviso) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filtro == null) {
                return cb.conjunction();
            }
            if (filtro.busca() != null && !filtro.busca().isBlank()) {
                List<Predicate> ors = new ArrayList<>();
                String digitos = filtro.busca().replaceAll("[^0-9]", "");
                if (digitos.length() == 11) {
                    ors.add(cb.equal(root.get("cnhNumeroHash"), HashUtils.gerarBlindIndex(digitos)));
                }
                if (idsColaboradorPorNome != null && !idsColaboradorPorNome.isEmpty()) {
                    ors.add(root.get("idColaborador").in(idsColaboradorPorNome));
                }
                predicates.add(ors.isEmpty() ? cb.disjunction() : cb.or(ors.toArray(new Predicate[0])));
            }
            if (idsColaboradorPorBase != null) {
                predicates.add(idsColaboradorPorBase.isEmpty()
                        ? cb.disjunction() : root.get("idColaborador").in(idsColaboradorPorBase));
            }
            SpecificationUtils.filtrarPor(filtro.categorias(), predicates, root.get("cnhCategoria"));
            if (filtro.situacaoCnh() != null) {
                var validade = root.<LocalDate>get("cnhValidade");
                switch (filtro.situacaoCnh()) {
                    case VENCIDO -> predicates.add(cb.lessThan(validade, hoje));
                    case A_VENCER -> predicates.add(cb.between(validade, hoje, hoje.plusDays(diasAviso)));
                    case VIGENTE -> predicates.add(cb.greaterThan(validade, hoje.plusDays(diasAviso)));
                    case SEM_VENCIMENTO -> predicates.add(cb.isNull(validade));
                }
            }
            if (filtro.ativo() != null) {
                predicates.add(cb.equal(root.get("ativo"), filtro.ativo()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
