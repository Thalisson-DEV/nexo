package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.application.dto.ObraFilter;
import com.nexo.app.modules.obras.domain.AgenteResponsavel;
import com.nexo.app.modules.obras.domain.ClasseTensao;
import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.obras.domain.PrioridadeObra;
import com.nexo.app.modules.obras.domain.RelacionamentoProjeto;
import com.nexo.app.modules.obras.domain.StatusObra;
import com.nexo.app.modules.rh.domain.BaseOperacional;
import com.nexo.app.modules.rh.domain.CentroFaturamento;
import com.nexo.app.modules.rh.domain.Colaborador;
import com.nexo.app.modules.rh.domain.Coordenador;
import com.nexo.app.modules.rh.domain.Municipio;
import com.nexo.app.modules.rh.domain.ProcessoNegocio;
import com.nexo.app.modules.rh.domain.Supervisor;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ObraSpecification {

    public static Specification<Obra> filterBy(ObraFilter filter) {
        return filterBy(filter, Sort.unsorted());
    }

    public static Specification<Obra> filterBy(ObraFilter filter, Sort sort) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter != null) {
                if (filter.numero() != null && !filter.numero().isBlank()) {
                    predicates.add(cb.like(cb.lower(root.get("numero")), "%" + filter.numero().trim().toLowerCase() + "%"));
                }
                if (filter.titulo() != null && !filter.titulo().isBlank()) {
                    predicates.add(cb.like(cb.lower(root.get("titulo")), "%" + filter.titulo().trim().toLowerCase() + "%"));
                }
                if (filter.notaObra() != null && !filter.notaObra().isBlank()) {
                    predicates.add(cb.like(cb.lower(root.get("notaObra")), "%" + filter.notaObra().trim().toLowerCase() + "%"));
                }
                if (filter.tipoObra() != null) {
                    predicates.add(cb.equal(root.get("tipoObra"), filter.tipoObra()));
                }
                if (filter.statusId() != null) {
                    predicates.add(cb.equal(root.get("statusObra").get("id"), filter.statusId()));
                }
                if (filter.statusIds() != null && !filter.statusIds().isEmpty()) {
                    SpecificationUtils.filtrarPor(filter.statusIds(), predicates, root.get("statusObra").get("id"));
                }
                if (filter.processoId() != null) {
                    predicates.add(cb.equal(root.get("idProcesso"), filter.processoId()));
                }
                if (filter.processoIds() != null && !filter.processoIds().isEmpty()) {
                    SpecificationUtils.filtrarPor(filter.processoIds(), predicates, root.get("idProcesso"));
                }
                if (filter.baseOperacionalId() != null) {
                    predicates.add(cb.equal(root.get("idBaseOperacional"), filter.baseOperacionalId()));
                }
                if (filter.baseOperacionalIds() != null && !filter.baseOperacionalIds().isEmpty()) {
                    SpecificationUtils.filtrarPor(filter.baseOperacionalIds(), predicates, root.get("idBaseOperacional"));
                }
                if (filter.municipioId() != null) {
                    predicates.add(cb.equal(root.get("idMunicipio"), filter.municipioId()));
                }
                if (filter.municipioIds() != null && !filter.municipioIds().isEmpty()) {
                    SpecificationUtils.filtrarPor(filter.municipioIds(), predicates, root.get("idMunicipio"));
                }
                if (filter.centroCustoId() != null) {
                    predicates.add(cb.equal(root.get("idCentroCusto"), filter.centroCustoId()));
                }
                if (filter.supervisorId() != null) {
                    predicates.add(cb.equal(root.get("idSupervisor"), filter.supervisorId()));
                }
                if (filter.supervisorIds() != null && !filter.supervisorIds().isEmpty()) {
                    SpecificationUtils.filtrarPor(filter.supervisorIds(), predicates, root.get("idSupervisor"));
                }
                if (filter.coordenadorId() != null) {
                    predicates.add(cb.equal(root.get("idCoordenador"), filter.coordenadorId()));
                }
                if (filter.coordenadorIds() != null && !filter.coordenadorIds().isEmpty()) {
                    SpecificationUtils.filtrarPor(filter.coordenadorIds(), predicates, root.get("idCoordenador"));
                }
                if (filter.agenteResponsavelId() != null) {
                    predicates.add(cb.equal(root.get("agenteResponsavel").get("id"), filter.agenteResponsavelId()));
                }
                if (filter.equipeId() != null) {
                    predicates.add(cb.isMember(filter.equipeId(), root.get("equipes")));
                }
                if (filter.projetoRelacionadoId() != null && query != null) {
                    Subquery<UUID> relacionamento = query.subquery(UUID.class);
                    Root<RelacionamentoProjeto> rel = relacionamento.from(RelacionamentoProjeto.class);
                    relacionamento.select(rel.get("id"));
                    relacionamento.where(
                            cb.isTrue(rel.get("ativo")),
                            cb.or(
                                    cb.and(
                                            cb.equal(rel.get("projetoA").get("id"), root.get("id")),
                                            cb.equal(rel.get("projetoB").get("id"), filter.projetoRelacionadoId())
                                    ),
                                    cb.and(
                                            cb.equal(rel.get("projetoB").get("id"), root.get("id")),
                                            cb.equal(rel.get("projetoA").get("id"), filter.projetoRelacionadoId())
                                    )
                            )
                    );
                    predicates.add(cb.exists(relacionamento));
                }
                if (Boolean.TRUE.equals(filter.apenasPrincipais())) {
                    predicates.add(cb.isNull(root.get("obraPai")));
                }
                if (filter.obraPaiId() != null) {
                    predicates.add(cb.equal(root.get("obraPai").get("id"), filter.obraPaiId()));
                }
                if (filter.dataInicioDe() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("dataInicio"), filter.dataInicioDe()));
                }
                if (filter.dataInicioAte() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("dataInicio"), filter.dataInicioAte()));
                }
                if (filter.dataPrevisaoFimDe() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("dataPrevisaoFim"), filter.dataPrevisaoFimDe()));
                }
                if (filter.dataPrevisaoFimAte() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("dataPrevisaoFim"), filter.dataPrevisaoFimAte()));
                }
                if (filter.valorProjetoMin() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("valorProjeto"), filter.valorProjetoMin()));
                }
                if (filter.valorProjetoMax() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("valorProjeto"), filter.valorProjetoMax()));
                }
                if (filter.ativo() != null) {
                    predicates.add(cb.equal(root.get("ativo"), filter.ativo()));
                } else {
                    predicates.add(cb.isTrue(root.get("ativo")));
                }

                SpecificationUtils.buscaRapida(filter.buscaGlobal(), predicates, cb, root, "numero", "titulo", "notaObra");
            } else {
                predicates.add(cb.isTrue(root.get("ativo")));
            }

            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                aplicarOrdenacoes(sort, root, query, cb);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static void aplicarOrdenacoes(Sort sort, Root<Obra> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        List<Order> orders = new ArrayList<>();
        if (sort != null && sort.isSorted()) {
            for (Sort.Order order : sort) {
                Order jpaOrder = construirOrdem(order, root, query, cb);
                if (jpaOrder != null) {
                    orders.add(jpaOrder);
                }
            }
        }

        if (orders.isEmpty()) {
            orders.add(cb.desc(root.get("dataCriacao")));
        }

        query.orderBy(orders);
    }

    private static Order construirOrdem(Sort.Order order, Root<Obra> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        String prop = order.getProperty().toLowerCase();
        boolean asc = order.isAscending();

        return switch (prop) {
            case "numero" -> asc ? cb.asc(root.get("numero")) : cb.desc(root.get("numero"));
            case "titulo" -> asc ? cb.asc(cb.lower(root.get("titulo"))) : cb.desc(cb.lower(root.get("titulo")));
            case "tipoobra", "tipo_obra" -> asc ? cb.asc(root.get("tipoObra")) : cb.desc(root.get("tipoObra"));
            case "notaobra", "nota_obra" -> asc ? cb.asc(root.get("notaObra")) : cb.desc(root.get("notaObra"));
            case "valorprojeto", "valor_projeto" -> asc ? cb.asc(root.get("valorProjeto")) : cb.desc(root.get("valorProjeto"));
            case "datainicio", "data_inicio" -> asc ? cb.asc(root.get("dataInicio")) : cb.desc(root.get("dataInicio"));
            case "dataprevisaofim", "data_previsao_fim" -> asc ? cb.asc(root.get("dataPrevisaoFim")) : cb.desc(root.get("dataPrevisaoFim"));
            case "datafimefetiva", "data_fim_efetiva" -> asc ? cb.asc(root.get("dataFimEfetiva")) : cb.desc(root.get("dataFimEfetiva"));
            case "ativo" -> asc ? cb.asc(root.get("ativo")) : cb.desc(root.get("ativo"));
            case "classetensao", "classe_tensao" -> {
                // Pela tensão (BT < 15 kV < ... < 138 kV), não pelo nome do enum.
                CriteriaBuilder.SimpleCase<ClasseTensao, Integer> nivel = cb.selectCase(root.get("classeTensao"));
                for (ClasseTensao classe : ClasseTensao.values()) {
                    nivel = nivel.when(classe, classe.ordinal());
                }
                yield asc ? cb.asc(nivel.otherwise(ClasseTensao.values().length)) : cb.desc(nivel.otherwise(-1));
            }
            case "prioridade" -> {
                CriteriaBuilder.SimpleCase<PrioridadeObra, Integer> nivel = cb.selectCase(root.get("prioridade"));
                for (PrioridadeObra prioridade : PrioridadeObra.values()) {
                    nivel = nivel.when(prioridade, prioridade.ordinal());
                }
                yield asc ? cb.asc(nivel.otherwise(PrioridadeObra.values().length)) : cb.desc(nivel.otherwise(-1));
            }
            case "prazoregulado", "prazo_regulado" -> asc ? cb.asc(root.get("prazoRegulado")) : cb.desc(root.get("prazoRegulado"));
            case "situacaocarteira", "situacao_carteira" -> asc ? cb.asc(cb.lower(root.get("situacaoCarteira"))) : cb.desc(cb.lower(root.get("situacaoCarteira")));
            case "valormaterialprevisto", "valor_material_previsto" -> asc ? cb.asc(root.get("valorMaterialPrevisto")) : cb.desc(root.get("valorMaterialPrevisto"));
            case "valorservicoprevisto", "valor_servico_previsto" -> asc ? cb.asc(root.get("valorServicoPrevisto")) : cb.desc(root.get("valorServicoPrevisto"));
            case "datacriacao", "data_criacao", "createdat" -> asc ? cb.asc(root.get("dataCriacao")) : cb.desc(root.get("dataCriacao"));
            case "dataultimaalteracao", "data_ultima_alteracao", "updatedat" -> asc ? cb.asc(root.get("dataUltimaAlteracao")) : cb.desc(root.get("dataUltimaAlteracao"));
            case "id" -> asc ? cb.asc(root.get("id")) : cb.desc(root.get("id"));

            // Status
            case "status", "statusnome", "status.nome", "statusobra.nome", "statusobra", "idstatusobra", "statusid" -> {
                Join<Obra, StatusObra> statusJoin = root.join("statusObra", JoinType.LEFT);
                yield asc ? cb.asc(statusJoin.get("nome")) : cb.desc(statusJoin.get("nome"));
            }
            case "statusconcluida", "status.concluida" -> {
                Join<Obra, StatusObra> statusJoin = root.join("statusObra", JoinType.LEFT);
                yield asc ? cb.asc(statusJoin.get("concluida")) : cb.desc(statusJoin.get("concluida"));
            }

            // Agente Responsavel
            case "agenteresponsavel", "agenteresponsavelnome", "agenteresponsavel.nome", "idagenteresponsavel", "agenteresponsavelid" -> {
                Join<Obra, AgenteResponsavel> agenteJoin = root.join("agenteResponsavel", JoinType.LEFT);
                yield asc ? cb.asc(agenteJoin.get("nome")) : cb.desc(agenteJoin.get("nome"));
            }

            // Obra Pai
            case "obrapai", "obrapainumero", "obrapai.numero", "obrapaiid", "idobrapai" -> {
                Join<Obra, Obra> paiJoin = root.join("obraPai", JoinType.LEFT);
                yield asc ? cb.asc(paiJoin.get("numero")) : cb.desc(paiJoin.get("numero"));
            }
            case "tipovinculopai", "tipo_vinculo_pai" -> asc ? cb.asc(root.get("tipoVinculoPai")) : cb.desc(root.get("tipoVinculoPai"));

            // Subprojetos / Anexos / Equipes
            case "totalsubprojetos", "total_subprojetos" -> asc ? cb.asc(cb.size(root.get("subprojetos"))) : cb.desc(cb.size(root.get("subprojetos")));
            case "totalanexos", "total_anexos" -> asc ? cb.asc(cb.size(root.get("anexos"))) : cb.desc(cb.size(root.get("anexos")));
            case "totalequipes", "total_equipes" -> asc ? cb.asc(cb.size(root.get("equipes"))) : cb.desc(cb.size(root.get("equipes")));

            // Processo (Cross-Module RH)
            case "processo", "processonome", "processo.nome", "idprocesso", "processoid" -> {
                Subquery<String> sub = query.subquery(String.class);
                Root<ProcessoNegocio> pRoot = sub.from(ProcessoNegocio.class);
                sub.select(pRoot.get("nome"));
                sub.where(cb.equal(pRoot.get("id"), root.get("idProcesso")));
                yield asc ? cb.asc(sub) : cb.desc(sub);
            }

            // Base Operacional (Cross-Module RH)
            case "baseoperacional", "baseoperacionalnome", "baseoperacional.nome", "idbaseoperacional", "baseoperacionalid" -> {
                Subquery<String> sub = query.subquery(String.class);
                Root<BaseOperacional> bRoot = sub.from(BaseOperacional.class);
                sub.select(bRoot.get("nome"));
                sub.where(cb.equal(bRoot.get("id"), root.get("idBaseOperacional")));
                yield asc ? cb.asc(sub) : cb.desc(sub);
            }

            // Municipio (Cross-Module RH)
            case "municipio", "municipionome", "municipio.nome", "idmunicipio", "municipioid" -> {
                Subquery<String> sub = query.subquery(String.class);
                Root<Municipio> mRoot = sub.from(Municipio.class);
                sub.select(mRoot.get("nome"));
                sub.where(cb.equal(mRoot.get("id"), root.get("idMunicipio")));
                yield asc ? cb.asc(sub) : cb.desc(sub);
            }

            // Centro de Custo / Faturamento (Cross-Module RH)
            case "centrocusto", "centrocustonome", "centrocusto.nome", "idcentrocusto", "centrocustoid" -> {
                Subquery<String> sub = query.subquery(String.class);
                Root<CentroFaturamento> cRoot = sub.from(CentroFaturamento.class);
                sub.select(cRoot.get("nome"));
                sub.where(cb.equal(cRoot.get("id"), root.get("idCentroCusto")));
                yield asc ? cb.asc(sub) : cb.desc(sub);
            }

            // Supervisor (Cross-Module RH)
            case "supervisor", "supervisornome", "supervisor.nome", "idsupervisor", "supervisorid" -> {
                Subquery<String> sub = query.subquery(String.class);
                Root<Supervisor> sRoot = sub.from(Supervisor.class);
                Join<Supervisor, Colaborador> colJoin = sRoot.join("colaborador");
                sub.select(colJoin.get("nomeBusca"));
                sub.where(cb.equal(sRoot.get("id"), root.get("idSupervisor")));
                yield asc ? cb.asc(sub) : cb.desc(sub);
            }

            // Coordenador (Cross-Module RH)
            case "coordenador", "coordenadornome", "coordenador.nome", "idcoordenador", "coordenadorid" -> {
                Subquery<String> sub = query.subquery(String.class);
                Root<Coordenador> cRoot = sub.from(Coordenador.class);
                Join<Coordenador, Colaborador> colJoin = cRoot.join("colaborador");
                sub.select(colJoin.get("nomeBusca"));
                sub.where(cb.equal(cRoot.get("id"), root.get("idCoordenador")));
                yield asc ? cb.asc(sub) : cb.desc(sub);
            }

            default -> null;
        };
    }
}
