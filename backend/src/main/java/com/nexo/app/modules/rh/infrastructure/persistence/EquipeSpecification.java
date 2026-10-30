package com.nexo.app.modules.rh.infrastructure.persistence;

import com.nexo.app.modules.rh.application.dto.EquipeFilter;
import com.nexo.app.modules.rh.domain.Equipe;
import com.nexo.app.modules.rh.domain.EquipeMembro;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Construtor dinâmico de queries para a entidade Equipe.
 */
public class EquipeSpecification {

    public static Specification<Equipe> filterBy(EquipeFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.codEquipe() != null && !filter.codEquipe().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("codigo")), "%" + filter.codEquipe().toLowerCase() + "%"));
            }

            if (filter.idTipoEquipe() != null) {
                predicates.add(cb.equal(root.get("tipoEquipe").get("id"), filter.idTipoEquipe()));
            }

            if (filter.idProcesso() != null) {
                predicates.add(cb.equal(root.get("processo").get("id"), filter.idProcesso()));
            }

            if (filter.idBaseOperacional() != null) {
                predicates.add(cb.equal(root.get("baseOperacional").get("id"), filter.idBaseOperacional()));
            }

            if (filter.idCoordenador() != null) {
                predicates.add(cb.equal(root.get("coordenador").get("id"), filter.idCoordenador()));
            }

            if (filter.idSupervisor() != null) {
                predicates.add(cb.equal(root.get("supervisor").get("id"), filter.idSupervisor()));
            }

            if (filter.idVeiculo() != null) {
                predicates.add(cb.equal(root.get("veiculo").get("id"), filter.idVeiculo()));
            }

            if (filter.ativo() != null) {
                predicates.add(cb.equal(root.get("ativo"), filter.ativo()));
            }

            if (filter.idMembro() != null) {
                Join<Equipe, EquipeMembro> join = root.join("membros");
                predicates.add(cb.equal(join.get("colaborador").get("id"), filter.idMembro()));
                predicates.add(cb.isNull(join.get("dataSaida")));
            }

            if (filter.idResponsavel() != null) {
                Join<Equipe, EquipeMembro> join = root.join("membros");
                predicates.add(cb.equal(join.get("colaborador").get("id"), filter.idResponsavel()));
                predicates.add(cb.equal(join.get("funcao"), EquipeMembro.FuncaoMembro.RESPONSAVEL));
                predicates.add(cb.isNull(join.get("dataSaida")));
            }

            if (filter.idParceiro() != null) {
                Join<Equipe, EquipeMembro> join = root.join("membros");
                predicates.add(cb.equal(join.get("colaborador").get("id"), filter.idParceiro()));
                predicates.add(cb.equal(join.get("funcao"), EquipeMembro.FuncaoMembro.PARCEIRO));
                predicates.add(cb.isNull(join.get("dataSaida")));
            }

            SpecificationUtils.buscaRapida(filter.buscaGlobal(), predicates, cb, root, "codigo");

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
