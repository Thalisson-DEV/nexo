package com.nexo.app.modules.chamado.infrastructure.persistence;

import com.nexo.app.modules.chamado.application.dto.DashboardChamadosFilter;
import com.nexo.app.modules.chamado.domain.Chamado;
import com.nexo.app.modules.chamado.domain.StatusChamado;
import com.nexo.app.shared.application.SelecaoMultipla;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Specifications para carregar as coortes de chamados que alimentam o dashboard.
 */
public final class ChamadoDashboardSpecification {

    private static final Set<StatusChamado> STATUS_PENDENTES = Set.of(
            StatusChamado.ABERTO,
            StatusChamado.EM_ATENDIMENTO,
            StatusChamado.AGUARDANDO_SOLICITANTE
    );

    private ChamadoDashboardSpecification() {}

    /**
     * Carrega os chamados abertos no intervalo informado respeitando os filtros selecionados.
     */
    public static Specification<Chamado> noEscopo(DashboardChamadosFilter filtro,
                                                 OffsetDateTime inicio,
                                                 OffsetDateTime fim) {
        return (root, query, cb) -> {
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("topico", JoinType.LEFT);
                root.fetch("equipeAtendimento", JoinType.LEFT);
                query.distinct(true);
            }

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("ativo")));

            if (inicio != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dataAbertura"), inicio));
            }
            if (fim != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dataAbertura"), fim));
            }

            aplicarFiltrosDimensoes(filtro, root, cb, predicates);

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Carrega todo o histórico de chamados pendentes acumulados até a data de referência fim.
     */
    public static Specification<Chamado> backlogAte(DashboardChamadosFilter filtro,
                                                    OffsetDateTime fim) {
        return (root, query, cb) -> {
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("topico", JoinType.LEFT);
                root.fetch("equipeAtendimento", JoinType.LEFT);
                query.distinct(true);
            }

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("ativo")));

            if (fim != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dataAbertura"), fim));
            }

            // Um chamado pertence ao backlog na data de referencia se:
            // 1) Seu status atual ainda e pendente; OU
            // 2) Foi resolvido/fechado/cancelado somente APOS a data fim informada.
            Predicate statusPendenteAtual = root.get("status").in(STATUS_PENDENTES);
            Predicate resolvidoAposFim = fim != null
                    ? cb.and(cb.isNotNull(root.get("dataResolucao")), cb.greaterThan(root.get("dataResolucao"), fim))
                    : cb.disjunction();
            Predicate fechadoAposFim = fim != null
                    ? cb.and(cb.isNotNull(root.get("dataFechamento")), cb.greaterThan(root.get("dataFechamento"), fim))
                    : cb.disjunction();

            predicates.add(cb.or(statusPendenteAtual, resolvidoAposFim, fechadoAposFim));

            aplicarFiltrosDimensoes(filtro, root, cb, predicates);

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static void aplicarFiltrosDimensoes(DashboardChamadosFilter filtro,
                                                jakarta.persistence.criteria.Root<Chamado> root,
                                                jakarta.persistence.criteria.CriteriaBuilder cb,
                                                List<Predicate> predicates) {
        if (filtro == null) return;

        if (!SelecaoMultipla.vazia(filtro.topicoId())) {
            predicates.add(root.get("topico").get("id").in(filtro.topicoId()));
        }
        if (!SelecaoMultipla.vazia(filtro.equipeId())) {
            predicates.add(root.get("equipeAtendimento").get("id").in(filtro.equipeId()));
        }
        if (!SelecaoMultipla.vazia(filtro.prioridade())) {
            predicates.add(root.get("prioridade").in(filtro.prioridade()));
        }
        if (!SelecaoMultipla.vazia(filtro.status())) {
            predicates.add(root.get("status").in(filtro.status()));
        }
        if (!SelecaoMultipla.vazia(filtro.usuarioResponsavelId())) {
            predicates.add(root.get("usuarioResponsavelId").in(filtro.usuarioResponsavelId()));
        }
        if (!SelecaoMultipla.vazia(filtro.usuarioSolicitanteId())) {
            predicates.add(root.get("usuarioSolicitanteId").in(filtro.usuarioSolicitanteId()));
        }
        if (Boolean.TRUE.equals(filtro.slaVencido())) {
            OffsetDateTime agora = OffsetDateTime.now();
            Predicate respFora = cb.and(
                    cb.isNotNull(root.get("dataPrimeiraResposta")),
                    cb.greaterThan(root.get("dataPrimeiraResposta"), root.get("dataLimitePrimeiraResposta")));
            Predicate semRespFora = cb.and(
                    cb.isNull(root.get("dataPrimeiraResposta")),
                    cb.lessThan(root.get("dataLimitePrimeiraResposta"), agora));

            Predicate resFora = cb.and(
                    cb.isNotNull(root.get("dataResolucao")),
                    cb.greaterThan(root.get("dataResolucao"), root.get("dataLimiteResolucao")));
            Predicate canceladoFora = cb.and(
                    cb.equal(root.get("status"), StatusChamado.CANCELADO),
                    cb.greaterThan(root.get("dataFechamento"), root.get("dataLimiteResolucao")));
            Predicate pausadoFora = cb.and(
                    cb.isNull(root.get("dataResolucao")),
                    cb.isNotNull(root.get("dataInicioPausaSla")),
                    cb.greaterThan(root.get("dataInicioPausaSla"), root.get("dataLimiteResolucao")));
            Predicate emCursoFora = cb.and(
                    cb.isNull(root.get("dataResolucao")),
                    cb.notEqual(root.get("status"), StatusChamado.CANCELADO),
                    cb.isNull(root.get("dataInicioPausaSla")),
                    cb.lessThan(root.get("dataLimiteResolucao"), agora));

            predicates.add(cb.or(respFora, semRespFora, resFora, canceladoFora, pausadoFora, emCursoFora));
        }
    }
}
