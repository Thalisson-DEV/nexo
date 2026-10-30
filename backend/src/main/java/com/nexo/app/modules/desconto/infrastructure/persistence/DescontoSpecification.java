package com.nexo.app.modules.desconto.infrastructure.persistence;

import com.nexo.app.modules.desconto.application.dto.DescontoFilter;
import com.nexo.app.modules.desconto.domain.Desconto;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class DescontoSpecification {

    public static Specification<Desconto> comFiltros(DescontoFilter filtro) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // Soft delete default
            predicates.add(cb.isTrue(root.get("ativo")));

            if (filtro != null) {
                if (filtro.colaboradorId() != null) {
                    predicates.add(cb.equal(root.get("colaboradorId"), filtro.colaboradorId()));
                }
                
                if (filtro.motivoId() != null) {
                    predicates.add(cb.equal(root.get("motivo").get("id"), filtro.motivoId()));
                }
                
                if (filtro.status() != null) {
                    predicates.add(cb.equal(root.get("status"), filtro.status()));
                }
                
                if (filtro.usuarioSolicitanteId() != null) {
                    predicates.add(cb.equal(root.get("usuarioSolicitanteId"), filtro.usuarioSolicitanteId()));
                }
                
                if (filtro.dataCriacaoInicio() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("dataCriacao"), filtro.dataCriacaoInicio()));
                }
                
                if (filtro.dataCriacaoFim() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("dataCriacao"), filtro.dataCriacaoFim()));
                }
                
                if (filtro.dataOcorrenciaInicio() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("dataOcorrencia"), filtro.dataOcorrenciaInicio()));
                }
                
                if (filtro.dataOcorrenciaFim() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("dataOcorrencia"), filtro.dataOcorrenciaFim()));
                }
                
                if (filtro.buscaGlobal() != null && !filtro.buscaGlobal().isBlank()) {
                    String termo = "%" + filtro.buscaGlobal().toLowerCase() + "%";
                    predicates.add(cb.like(cb.lower(root.get("observacoes")), termo));
                }
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
