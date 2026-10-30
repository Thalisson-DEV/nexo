package com.nexo.app.modules.frota.infrastructure.persistence;

import com.nexo.app.modules.frota.application.dto.FichaVeiculoFilter;
import com.nexo.app.modules.frota.domain.FichaVeiculo;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FichaVeiculoSpecification {

    private FichaVeiculoSpecification() {
    }

    public static Specification<FichaVeiculo> of(FichaVeiculoFilter filtro) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filtro == null) {
                return cb.conjunction();
            }
            if (filtro.busca() != null && !filtro.busca().isBlank()) {
                String termo = "%" + filtro.busca().trim().toUpperCase(Locale.ROOT).replace("-", "") + "%";
                predicates.add(cb.or(
                        cb.like(root.get("placa"), termo),
                        cb.like(cb.upper(root.get("chassi")), termo),
                        cb.like(root.get("renavam"), termo)));
            }
            SpecificationUtils.filtrarPor(filtro.idsBase(), predicates, root.get("idBaseOperacional"));
            SpecificationUtils.filtrarPor(filtro.idsTipoVeiculo(), predicates, root.get("tipoVeiculo").get("id"));
            SpecificationUtils.filtrarPor(filtro.situacoes(), predicates, root.get("situacao"));
            if (filtro.regime() != null) {
                predicates.add(cb.equal(root.get("regime"), filtro.regime()));
            }
            if (filtro.ativo() != null) {
                predicates.add(cb.equal(root.get("ativo"), filtro.ativo()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
