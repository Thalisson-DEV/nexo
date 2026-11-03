package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.application.dto.MedicaoFilter;
import com.nexo.app.modules.obras.domain.Medicao;
import com.nexo.app.modules.obras.domain.Obra;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MedicaoSpecification {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private MedicaoSpecification() {
    }

    public static Specification<Medicao> filterBy(MedicaoFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("ativo")));
            if (filter == null) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }
            if (filter.idObra() != null) {
                predicates.add(cb.equal(root.get("idObra"), filter.idObra()));
            }
            if (filter.tipos() != null && !filter.tipos().isEmpty()) {
                predicates.add(root.get("tipo").in(filter.tipos()));
            }
            if (filter.status() != null && !filter.status().isEmpty()) {
                predicates.add(root.get("status").in(filter.status()));
            }
            if (filter.competenciaDe() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("competencia"), filter.competenciaDe().atDay(1)));
            }
            if (filter.competenciaAte() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("competencia"), filter.competenciaAte().atDay(1)));
            }
            boolean porBase = filter.idsBases() != null && !filter.idsBases().isEmpty();
            boolean porMunicipio = filter.idsMunicipios() != null && !filter.idsMunicipios().isEmpty();
            boolean porSupervisor = filter.idsSupervisores() != null && !filter.idsSupervisores().isEmpty();
            if (porBase || porMunicipio || porSupervisor) {
                Subquery<UUID> obras = query.subquery(UUID.class);
                Root<Obra> obra = obras.from(Obra.class);
                List<Predicate> daObra = new ArrayList<>();
                if (porBase) daObra.add(obra.get("idBaseOperacional").in(filter.idsBases()));
                if (porMunicipio) daObra.add(obra.get("idMunicipio").in(filter.idsMunicipios()));
                if (porSupervisor) daObra.add(obra.get("idSupervisor").in(filter.idsSupervisores()));
                obras.select(obra.get("id")).where(daObra.toArray(new Predicate[0]));
                predicates.add(root.get("idObra").in(obras));
            }
            if (filter.usuario() != null && !filter.usuario().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("usuarioCriacao")), "%" + filter.usuario().trim().toLowerCase() + "%"));
            }
            if (filter.geradaDe() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dataCriacao"), filter.geradaDe().atStartOfDay(FUSO).toOffsetDateTime()));
            }
            if (filter.geradaAte() != null) {
                predicates.add(cb.lessThan(root.get("dataCriacao"), filter.geradaAte().plusDays(1).atStartOfDay(FUSO).toOffsetDateTime()));
            }
            if (filter.q() != null && !filter.q().isBlank()) {
                String texto = filter.q().trim();
                String termo = "%" + texto.toLowerCase() + "%";
                Subquery<UUID> obras = query.subquery(UUID.class);
                Root<Obra> obra = obras.from(Obra.class);
                obras.select(obra.get("id")).where(cb.or(
                        cb.like(cb.lower(obra.get("numero")), termo),
                        cb.like(cb.lower(obra.get("titulo")), termo)));
                List<Predicate> campos = new ArrayList<>(List.of(root.get("idObra").in(obras)));
                String numero = texto.startsWith("#") ? texto.substring(1) : texto;
                if (numero.matches("\\d{1,18}")) {
                    campos.add(cb.equal(root.get("numero"), Long.valueOf(numero)));
                }
                predicates.add(cb.or(campos.toArray(new Predicate[0])));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
