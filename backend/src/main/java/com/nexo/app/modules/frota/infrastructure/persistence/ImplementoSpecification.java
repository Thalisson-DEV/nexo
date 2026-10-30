package com.nexo.app.modules.frota.infrastructure.persistence;

import com.nexo.app.modules.frota.application.dto.ImplementoFilter;
import com.nexo.app.modules.frota.domain.AcoplamentoImplemento;
import com.nexo.app.modules.frota.domain.Implemento;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class ImplementoSpecification {

    private ImplementoSpecification() {
    }

    public static Specification<Implemento> of(ImplementoFilter filtro) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filtro == null) {
                return cb.conjunction();
            }
            SpecificationUtils.buscaRapida(filtro.busca(), predicates, cb, root,
                    "identificacao", "fabricante", "modelo", "numeroSerie");
            SpecificationUtils.filtrarPor(filtro.idsBase(), predicates, root.get("idBaseOperacional"));
            SpecificationUtils.filtrarPor(filtro.idsTipoImplemento(), predicates, root.get("tipoImplemento").get("id"));
            SpecificationUtils.filtrarPor(filtro.situacoes(), predicates, root.get("situacao"));
            if (filtro.ativo() != null) {
                predicates.add(cb.equal(root.get("ativo"), filtro.ativo()));
            }
            if (filtro.idVeiculo() != null || filtro.acoplado() != null) {
                Subquery<Integer> aberto = query.subquery(Integer.class);
                var acoplamento = aberto.from(AcoplamentoImplemento.class);
                List<Predicate> condicoes = new ArrayList<>();
                condicoes.add(cb.equal(acoplamento.get("implemento"), root));
                condicoes.add(cb.isNull(acoplamento.get("fim")));
                condicoes.add(cb.isTrue(acoplamento.get("ativo")));
                if (filtro.idVeiculo() != null) {
                    condicoes.add(cb.equal(acoplamento.get("idVeiculo"), filtro.idVeiculo()));
                }
                aberto.select(cb.literal(1)).where(condicoes.toArray(new Predicate[0]));
                boolean exigeAcoplado = filtro.idVeiculo() != null || Boolean.TRUE.equals(filtro.acoplado());
                predicates.add(exigeAcoplado ? cb.exists(aberto) : cb.not(cb.exists(aberto)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
