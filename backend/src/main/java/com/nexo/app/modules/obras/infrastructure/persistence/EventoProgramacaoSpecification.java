package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.application.dto.EventoProgramacaoFilter;
import com.nexo.app.modules.obras.domain.EventoProgramacao;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public final class EventoProgramacaoSpecification {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private EventoProgramacaoSpecification() {
    }

    private static boolean temTexto(String texto) {
        return texto != null && !texto.isBlank();
    }

    private static String contem(String texto) {
        return "%" + texto.trim().toLowerCase() + "%";
    }

    public static Specification<EventoProgramacao> filterBy(EventoProgramacaoFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("ativo")));
            if (filter != null) {
                if (filter.tipos() != null && !filter.tipos().isEmpty()) {
                    predicates.add(root.get("tipo").in(filter.tipos()));
                }
                if (temTexto(filter.obra())) {
                    predicates.add(cb.like(cb.lower(root.get("numeroObra")), contem(filter.obra())));
                }
                if (temTexto(filter.equipe())) {
                    predicates.add(cb.or(cb.like(cb.lower(root.get("equipeOrigem")), contem(filter.equipe())),
                            cb.like(cb.lower(root.get("equipeDestino")), contem(filter.equipe()))));
                }
                if (temTexto(filter.usuario())) {
                    predicates.add(cb.like(cb.lower(root.get("usuarioCriacao")), contem(filter.usuario())));
                }
                if ("EQUIPE".equals(filter.mudou())) {
                    predicates.add(cb.notEqual(root.get("idEquipeOrigem"), root.get("idEquipeDestino")));
                } else if ("PERIODO".equals(filter.mudou())) {
                    predicates.add(cb.or(cb.notEqual(root.get("inicioOriginal"), root.get("inicioNovo")),
                            cb.notEqual(root.get("fimOriginal"), root.get("fimNovo"))));
                }
                if (filter.de() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("dataCriacao"), filter.de().atStartOfDay(FUSO).toOffsetDateTime()));
                }
                if (filter.ate() != null) {
                    predicates.add(cb.lessThan(root.get("dataCriacao"), filter.ate().plusDays(1).atStartOfDay(FUSO).toOffsetDateTime()));
                }
                if (filter.idObra() != null) {
                    predicates.add(cb.equal(root.get("idObra"), filter.idObra()));
                }
                if (filter.idProgramacao() != null) {
                    predicates.add(cb.equal(root.get("idProgramacao"), filter.idProgramacao()));
                }
                if (filter.q() != null && !filter.q().isBlank()) {
                    String texto = filter.q().trim();
                    String termo = "%" + texto.toLowerCase() + "%";
                    List<Predicate> campos = new ArrayList<>(List.of(
                            cb.like(cb.lower(root.get("numeroObra")), termo),
                            cb.like(cb.lower(root.get("equipeOrigem")), termo),
                            cb.like(cb.lower(root.get("equipeDestino")), termo),
                            cb.like(cb.lower(root.get("observacao")), termo),
                            cb.like(cb.lower(root.get("usuarioCriacao")), termo)));
                    String numero = texto.startsWith("#") ? texto.substring(1) : texto;
                    if (numero.matches("\\d{1,18}")) {
                        campos.add(cb.equal(root.get("numeroProgramacao"), Long.valueOf(numero)));
                    }
                    predicates.add(cb.or(campos.toArray(new Predicate[0])));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
