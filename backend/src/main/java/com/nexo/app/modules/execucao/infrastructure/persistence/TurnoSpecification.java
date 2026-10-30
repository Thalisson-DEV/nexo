package com.nexo.app.modules.execucao.infrastructure.persistence;

import com.nexo.app.modules.execucao.application.dto.ConsultaTurnos;
import com.nexo.app.modules.execucao.domain.Turno;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Filtros da consulta paginada de turnos. */
public final class TurnoSpecification {

    private TurnoSpecification() {
    }

    public static Specification<Turno> filtrarPor(ConsultaTurnos consulta) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();
            predicados.add(cb.isTrue(root.get("ativo")));

            if (consulta.idsEquipes() != null) {
                if (consulta.idsEquipes().isEmpty()) {
                    return cb.disjunction();
                }
                predicados.add(root.get("idEquipe").in(consulta.idsEquipes()));
            }
            if (consulta.status() != null) {
                predicados.add(cb.equal(root.get("status"), consulta.status()));
            }
            if (consulta.aberturaDe() != null) {
                predicados.add(cb.greaterThanOrEqualTo(root.get("dataInicio"), consulta.aberturaDe()));
            }
            if (consulta.aberturaAte() != null) {
                predicados.add(cb.lessThan(root.get("dataInicio"), consulta.aberturaAte()));
            }
            // Mesma regra de sobreposição do dashboard: aberto antes do fim e não fechado antes do início.
            if (consulta.ativoDe() != null && consulta.ativoAte() != null) {
                predicados.add(cb.lessThan(root.get("dataInicio"), consulta.ativoAte()));
                predicados.add(cb.or(
                        cb.isNull(root.get("dataFim")),
                        cb.greaterThanOrEqualTo(root.get("dataFim"), consulta.ativoDe())));
            }
            return cb.and(predicados.toArray(new Predicate[0]));
        };
    }
}
