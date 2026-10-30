package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.application.dto.ExigenciaTreinamentoSstFilter;
import com.nexo.app.modules.sst.domain.ExigenciaTreinamentoSst;
import com.nexo.app.shared.application.SelecaoMultipla;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class ExigenciaTreinamentoSstSpecification {

    private ExigenciaTreinamentoSstSpecification() {
    }

    public static Specification<ExigenciaTreinamentoSst> comFiltros(
            ExigenciaTreinamentoSstFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter == null || filter.ativa() == null) {
                predicates.add(cb.isTrue(root.get("ativo")));
            } else {
                predicates.add(cb.equal(root.get("ativo"), filter.ativa()));
            }
            if (filter != null) {
                if (!SelecaoMultipla.vazia(filter.idTreinamento())) {
                    predicates.add(root.get("idTreinamento").in(filter.idTreinamento()));
                }
                if (!SelecaoMultipla.vazia(filter.tipoCorpo())) {
                    predicates.add(root.get("tipoCorpo").in(filter.tipoCorpo()));
                }
                if (!SelecaoMultipla.vazia(filter.modoAplicabilidade())) {
                    predicates.add(root.get("modoAplicabilidade").in(filter.modoAplicabilidade()));
                }
                if (!SelecaoMultipla.vazia(filter.idFuncao())) {
                    predicates.add(root.get("idFuncao").in(filter.idFuncao()));
                }
                if (!SelecaoMultipla.vazia(filter.papelCorpo())) {
                    predicates.add(root.get("papelCorpo").in(filter.papelCorpo()));
                }
                if (!SelecaoMultipla.vazia(filter.idBase())) {
                    predicates.add(root.get("idBase").in(filter.idBase()));
                }
                if (filter.atual() != null) {
                    predicates.add(cb.equal(root.get("atual"), filter.atual()));
                }
                if (filter.fonteConfirmada() != null) {
                    predicates.add(cb.equal(root.get("fonteConfirmada"), filter.fonteConfirmada()));
                }
                LocalDate vigenteEm = filter.vigenteEm();
                if (vigenteEm != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("vigenciaInicio"), vigenteEm));
                    predicates.add(cb.or(cb.isNull(root.get("vigenciaFim")),
                            cb.greaterThanOrEqualTo(root.get("vigenciaFim"), vigenteEm)));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
