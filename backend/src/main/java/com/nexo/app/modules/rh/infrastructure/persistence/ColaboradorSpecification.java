package com.nexo.app.modules.rh.infrastructure.persistence;

import com.nexo.app.modules.rh.application.dto.ColaboradorFilter;
import com.nexo.app.modules.rh.domain.Colaborador;
import com.nexo.app.modules.rh.domain.EquipeMembro;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import com.nexo.app.shared.infrastructure.security.HashUtils;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Construtor dinâmico de queries para a entidade Colaborador seguindo Criteria API.
 * Suporta filtragem por dados sensíveis via Blind Index.
 */
public class ColaboradorSpecification {

    /**
     * Efetivo ativo dentro de um escopo organizacional. Bases e centros de custo aceitam
     * múltipla seleção; vazios, não restringem. Serve de denominador para os indicadores
     * de cobertura consumidos por outros módulos via {@code ColaboradorApi}.
     */
    public static Specification<Colaborador> ativosNoEscopo(Collection<UUID> idsBaseOperacional,
                                                            Collection<UUID> idsCentroCusto) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("ativo")));
            SpecificationUtils.filtrarPor(idsBaseOperacional, predicates, root.get("baseOperacional").get("id"));
            SpecificationUtils.filtrarPor(idsCentroCusto, predicates,
                    root.get("baseOperacional").get("centroFaturamento").get("id"));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Colaborador> filterBy(ColaboradorFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.ativo() != null) {
                predicates.add(cb.equal(root.get("ativo"), filter.ativo()));
            }

            if (filter.idBaseOperacional() != null) {
                predicates.add(cb.equal(root.get("baseOperacional").get("id"), filter.idBaseOperacional()));
            }

            if (filter.idProcesso() != null) {
                predicates.add(cb.equal(root.get("processo").get("id"), filter.idProcesso()));
            }

            if (filter.idFuncao() != null) {
                predicates.add(cb.equal(root.get("funcao").get("id"), filter.idFuncao()));
            }

            // Busca por função na equipe (RESPONSAVEL / PARCEIRO)
            if (filter.funcaoMembroEquipe() != null) {
                Subquery<UUID> subquery = query.subquery(UUID.class);
                Root<EquipeMembro> subRoot = subquery.from(EquipeMembro.class);
                subquery.select(subRoot.get("colaborador").get("id"))
                        .where(
                            cb.equal(subRoot.get("funcao"), filter.funcaoMembroEquipe()),
                            cb.isNull(subRoot.get("dataSaida"))
                        );
                predicates.add(root.get("id").in(subquery));
            }

            // Busca exata via Blind Index (Hash)
            if (filter.cpf() != null && !filter.cpf().isBlank()) {
                String hashCpf = HashUtils.gerarBlindIndex(filter.cpf());
                predicates.add(cb.equal(root.get("cpfHash"), hashCpf));
            }

            if (filter.nomeCompleto() != null && !filter.nomeCompleto().isBlank()) {
                String hashNome = HashUtils.gerarBlindIndex(filter.nomeCompleto());
                predicates.add(cb.equal(root.get("nomeCompletoHash"), hashNome));
            }

            if (filter.dataContratacaoInicio() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dataContratacao"), filter.dataContratacaoInicio()));
            }

            if (filter.dataContratacaoFim() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dataContratacao"), filter.dataContratacaoFim()));
            }

            // Filtro específico para Mês e Ano de Contratação
            if (filter.mesContratacao() != null) {
                predicates.add(cb.equal(cb.function("date_part", Integer.class, cb.literal("month"), root.get("dataContratacao")), filter.mesContratacao()));
            }

            if (filter.anoContratacao() != null) {
                predicates.add(cb.equal(cb.function("date_part", Integer.class, cb.literal("year"), root.get("dataContratacao")), filter.anoContratacao()));
            }

            // Busca rápida parcial por nome (texto puro normalizado)
            SpecificationUtils.buscaRapida(filter.buscaGlobal(), predicates, cb, root, "nomeBusca");

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
