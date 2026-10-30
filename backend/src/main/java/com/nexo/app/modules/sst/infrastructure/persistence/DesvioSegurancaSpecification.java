package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.application.dto.DesvioSegurancaFilter;
import com.nexo.app.modules.sst.application.dto.MeuDesvioFilter;
import com.nexo.app.modules.sst.domain.DesvioColaborador;
import com.nexo.app.modules.sst.domain.DesvioSeguranca;
import com.nexo.app.modules.sst.domain.GravidadeDesvio;
import com.nexo.app.modules.sst.domain.InspecaoSeguranca;
import com.nexo.app.modules.sst.domain.StatusDesvio;
import com.nexo.app.shared.application.SelecaoMultipla;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import com.nexo.app.modules.rh.domain.Colaborador;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class DesvioSegurancaSpecification {

    private DesvioSegurancaSpecification() {
    }

    /**
     * Listagem geral: todos os desvios da empresa, sem recorte pelo usuário autenticado.
     * Serve à visão do SESMT, em que a unidade da consulta é o desvio — não a inspeção.
     */
    public static Specification<DesvioSeguranca> geral(DesvioSegurancaFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<DesvioSeguranca, InspecaoSeguranca> inspecao = root.join("inspecao");

            predicates.add(cb.isTrue(root.get("ativo")));
            predicates.add(cb.isTrue(inspecao.get("ativo")));

            if (filter != null) {
                filtrarTratamento(predicates, cb, root, filter.status(), filter.apenasPendentes(),
                        filter.gravidade(), filter.apenasVencidos());
                filtrarEscopo(predicates, query, cb, inspecao, filter.idProcesso(), filter.idProcessoInspetor(),
                        filter.idBaseOperacional(), filter.idCentroCusto(), filter.idEquipe());

                SpecificationUtils.filtrarPor(filter.idInspetor(), predicates, inspecao.get("idInspetor"));
                SpecificationUtils.filtrarPor(filter.idFormulario(), predicates, inspecao.get("idFormulario"));
                SpecificationUtils.filtrarPor(filter.idVeiculo(), predicates, inspecao.get("idVeiculo"));
                SpecificationUtils.filtrarPor(filter.idPergunta(), predicates, root.get("idPergunta"));

                // O desvio é atribuído a um ou mais colaboradores: o recorte por pessoa
                // exige o join na coleção — daí o distinct ao final.
                if (!SelecaoMultipla.vazia(filter.idColaborador())) {
                    Join<DesvioSeguranca, DesvioColaborador> colaboradores = root.join("colaboradores");
                    predicates.add(colaboradores.get("idColaborador").in(filter.idColaborador()));
                }

                filtrarPeriodoDaInspecao(predicates, cb, inspecao, filter.dataInspecaoDe(), filter.dataInspecaoAte());

                if (filter.prazoDe() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("prazo"), filter.prazoDe()));
                }
                if (filter.prazoAte() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("prazo"), filter.prazoAte()));
                }
                if (Boolean.TRUE.equals(filter.semPrazo())) {
                    predicates.add(cb.isNull(root.get("prazo")));
                }
                if (filter.dataResolucaoDe() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("dataResolucao"), filter.dataResolucaoDe()));
                }
                if (filter.dataResolucaoAte() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("dataResolucao"), filter.dataResolucaoAte()));
                }

                filtrarBuscaRapida(predicates, cb, root, filter.buscaGlobal());
            }

            query.distinct(true);
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Um desvio específico, ativo e de uma inspeção ativa. */
    public static Specification<DesvioSeguranca> porId(UUID idDesvio) {
        return (root, query, cb) -> {
            Join<DesvioSeguranca, InspecaoSeguranca> inspecao = root.join("inspecao");
            return cb.and(
                    cb.equal(root.get("id"), idDesvio),
                    cb.isTrue(root.get("ativo")),
                    cb.isTrue(inspecao.get("ativo"))
            );
        };
    }

    public static Specification<DesvioSeguranca> doInspetor(UUID idInspetor, MeuDesvioFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<DesvioSeguranca, InspecaoSeguranca> inspecao = root.join("inspecao");

            predicates.add(cb.equal(inspecao.get("idInspetor"), idInspetor));
            predicates.add(cb.isTrue(root.get("ativo")));
            predicates.add(cb.isTrue(inspecao.get("ativo")));

            if (filter != null) {
                filtrarTratamento(predicates, cb, root, filter.status(), filter.apenasPendentes(),
                        filter.gravidade(), filter.apenasVencidos());
                filtrarEscopo(predicates, query, cb, inspecao, filter.idProcesso(), filter.idProcessoInspetor(),
                        filter.idBaseOperacional(), filter.idCentroCusto(), filter.idEquipe());
                filtrarPeriodoDaInspecao(predicates, cb, inspecao, filter.dataInspecaoDe(), filter.dataInspecaoAte());
                filtrarBuscaRapida(predicates, cb, root, filter.buscaGlobal());
            }

            query.distinct(true);
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    public static Specification<DesvioSeguranca> idsDoInspetor(
            Collection<UUID> idsDesvios, UUID idInspetor) {
        return (root, query, cb) -> {
            Join<DesvioSeguranca, InspecaoSeguranca> inspecao = root.join("inspecao");
            query.distinct(true);
            return cb.and(
                    root.get("id").in(idsDesvios),
                    cb.equal(inspecao.get("idInspetor"), idInspetor),
                    cb.isTrue(root.get("ativo")),
                    cb.isTrue(inspecao.get("ativo"))
            );
        };
    }

    // --------------------------------------------------------------------- //
    //  Recortes comuns às duas visões (minha caixa e listagem geral)
    // --------------------------------------------------------------------- //

    /** Estado do tratamento: status, gravidade e os atalhos "pendentes" e "vencidos". */
    private static void filtrarTratamento(List<Predicate> predicates, CriteriaBuilder cb,
                                          Root<DesvioSeguranca> root,
                                          Collection<StatusDesvio> status, Boolean apenasPendentes,
                                          Collection<GravidadeDesvio> gravidade, Boolean apenasVencidos) {
        SpecificationUtils.filtrarPor(status, predicates, root.get("status"));
        SpecificationUtils.filtrarPor(gravidade, predicates, root.get("gravidade"));

        if (Boolean.TRUE.equals(apenasPendentes)) {
            predicates.add(cb.notEqual(root.get("status"), StatusDesvio.RESOLVIDO));
        }
        if (Boolean.TRUE.equals(apenasVencidos)) {
            predicates.add(cb.lessThan(root.get("prazo"), LocalDate.now()));
            predicates.add(cb.notEqual(root.get("status"), StatusDesvio.RESOLVIDO));
        }
    }

    /** Onde o desvio aconteceu — dimensões que vivem na inspeção de origem. */
    private static void filtrarEscopo(List<Predicate> predicates, CriteriaQuery<?> query, CriteriaBuilder cb,
                                      Join<DesvioSeguranca, InspecaoSeguranca> inspecao,
                                      Collection<UUID> idProcesso, Collection<UUID> idProcessoInspetor,
                                      Collection<UUID> idBaseOperacional,
                                      Collection<UUID> idCentroCusto, Collection<UUID> idEquipe) {
        // Processo da inspeção (atividade/equipe operacional inspecionada)
        SpecificationUtils.filtrarPor(idProcesso, predicates, inspecao.get("idProcesso"));

        // Processo do inspetor (quem realizou a inspeção)
        if (!SelecaoMultipla.vazia(idProcessoInspetor)) {
            Subquery<UUID> subColabUser = query.subquery(UUID.class);
            Root<Colaborador> colabUser = subColabUser.from(Colaborador.class);
            subColabUser.select(colabUser.get("idUsuario"))
                    .where(
                            cb.isNotNull(colabUser.get("idUsuario")),
                            colabUser.get("processo").get("id").in(idProcessoInspetor)
                    );

            Subquery<UUID> subColabId = query.subquery(UUID.class);
            Root<Colaborador> colabId = subColabId.from(Colaborador.class);
            subColabId.select(colabId.get("id"))
                    .where(
                            colabId.get("processo").get("id").in(idProcessoInspetor)
                    );

            predicates.add(cb.or(
                    inspecao.get("idInspetor").in(subColabUser),
                    inspecao.get("idInspetor").in(subColabId)
            ));
        }
        SpecificationUtils.filtrarPor(idBaseOperacional, predicates, inspecao.get("idBaseOperacional"));
        SpecificationUtils.filtrarPor(idEquipe, predicates, inspecao.get("idEquipe"));

        // A inspeção guarda apenas a base; o centro de custo é resolvido nas bases dele.
        if (!SelecaoMultipla.vazia(idCentroCusto)) {
            predicates.add(inspecao.get("idBaseOperacional")
                    .in(EscopoOrganizacional.basesDosCentrosCusto(query, cb, idCentroCusto)));
        }
    }

    private static void filtrarPeriodoDaInspecao(List<Predicate> predicates, CriteriaBuilder cb,
                                                 Join<DesvioSeguranca, InspecaoSeguranca> inspecao,
                                                 OffsetDateTime de, OffsetDateTime ate) {
        if (de != null) {
            predicates.add(cb.greaterThanOrEqualTo(inspecao.get("dataRealizacao"), de));
        }
        if (ate != null) {
            predicates.add(cb.lessThanOrEqualTo(inspecao.get("dataRealizacao"), ate));
        }
    }

    private static void filtrarBuscaRapida(List<Predicate> predicates, CriteriaBuilder cb,
                                           Root<DesvioSeguranca> root, String termo) {
        SpecificationUtils.buscaRapida(termo, predicates, cb,
                List.<Expression<String>>of(root.get("descricao"), root.get("acaoCorretiva")));
    }
}
