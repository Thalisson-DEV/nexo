package com.nexo.app.modules.estoque.infrastructure.persistence;

import com.nexo.app.modules.estoque.application.dto.SolicitacaoFilter;
import com.nexo.app.modules.estoque.domain.ItemSolicitacao;
import com.nexo.app.modules.estoque.domain.NormalizadorOcorrenciaTecnica;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.EtapaSolicitacaoTecnica;
import com.nexo.app.modules.estoque.domain.StatusSeparacao;
import com.nexo.app.modules.estoque.domain.TipoObraSnapshot;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
import com.nexo.app.shared.infrastructure.persistence.ReferenciaUtils;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class SolicitacaoSpecification {

    private SolicitacaoSpecification() {}

    public static Specification<SolicitacaoMaterial> filterBy(SolicitacaoFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            if (filter.idEstoqueOrigem() != null) {
                predicates.add(cb.equal(root.get("estoqueOrigem").get("id"), filter.idEstoqueOrigem()));
            }

            if (filter.idEstoqueDestino() != null) {
                predicates.add(cb.equal(root.get("estoqueDestino").get("id"), filter.idEstoqueDestino()));
            }

            if (filter.idBaseOrigem() != null) {
                predicates.add(cb.equal(root.get("estoqueOrigem").get("idBaseOperacional"), filter.idBaseOrigem()));
            }

            if (filter.idBaseDestino() != null) {
                predicates.add(cb.equal(root.get("estoqueDestino").get("idBaseOperacional"), filter.idBaseDestino()));
            }

            if (filter.idRequisitante() != null) {
                predicates.add(cb.equal(root.get("idRequisitante"), filter.idRequisitante()));
            }

            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }

            if (filter.statuses() != null && !filter.statuses().isEmpty()) {
                predicates.add(root.get("status").in(filter.statuses()));
            }
            if (filter.contexto() != null) {
                predicates.add(cb.equal(root.get("contexto"), filter.contexto()));
            }
            if (filter.tipoTecnico() != null) {
                predicates.add(cb.equal(root.get("tipoTecnico"), filter.tipoTecnico()));
            }
            if (filter.tipoObra() != null) {
                predicates.add(cb.equal(root.get("tipoObraSnapshot"), filter.tipoObra()));
            }
            if (filter.idObra() != null) {
                predicates.add(cb.equal(root.get("idObra"), filter.idObra()));
            }
            if (filter.ocorrencia() != null && !filter.ocorrencia().isBlank()) {
                predicates.add(cb.equal(root.get("ocorrencia"), NormalizadorOcorrenciaTecnica.normalizar(filter.ocorrencia())));
            }
            if (filter.idEquipe() != null) {
                predicates.add(cb.equal(root.get("idEquipeSaque"), filter.idEquipe()));
            }
            if (filter.idAlmoxarifado() != null) {
                predicates.add(cb.equal(root.get("almoxarifadoAtendimento").get("id"), filter.idAlmoxarifado()));
            }
            if (filter.numeroReserva() != null && !filter.numeroReserva().isBlank()) {
                predicates.add(cb.equal(root.get("reserva").get("numero"), numeroReserva(filter.numeroReserva())));
            }
            if (filter.idAgenteReserva() != null) {
                predicates.add(cb.equal(root.get("reserva").get("idAgenteResponsavel"), filter.idAgenteReserva()));
            }
            if (filter.statusSeparacao() != null) {
                predicates.add(cb.equal(root.get("statusSeparacao"), filter.statusSeparacao()));
            }
            if (filter.etapa() != null) {
                predicates.add(predicadoDaEtapa(filter.etapa(), root, query, cb));
            }

            // Filtro por Referência (Mês/Ano)
            OffsetDateTime inicioMes = ReferenciaUtils.converterParaDataInicio(filter.referencia());
            OffsetDateTime fimMes = ReferenciaUtils.converterParaDataFim(filter.referencia());
            if (inicioMes != null && fimMes != null) {
                predicates.add(cb.between(root.get("dataSolicitacao"), inicioMes, fimMes));
            }

            if (filter.dataInicio() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dataSolicitacao"), filter.dataInicio()));
            }

            if (filter.dataFim() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dataSolicitacao"), filter.dataFim()));
            }

            if (filter.previstaDe() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dataPrevistaRetirada"), filter.previstaDe()));
            }

            if (filter.previstaAte() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dataPrevistaRetirada"), filter.previstaAte()));
            }

            if (filter.numSerie() != null && !filter.numSerie().isBlank()) {
                Join<SolicitacaoMaterial, ItemSolicitacao> joinItens = root.join("itens");
                predicates.add(cb.like(cb.lower(joinItens.get("numSerie")), "%" + filter.numSerie().toLowerCase() + "%"));
                
                // Garantir que não retorne duplicatas se múltiplos itens derem match
                query.distinct(true);
            }

            // Busca rápida pelo número de série de algum item da solicitação
            if (filter.buscaGlobal() != null && !filter.buscaGlobal().isBlank()) {
                Join<SolicitacaoMaterial, ItemSolicitacao> joinBusca = root.join("itens");
                SpecificationUtils.buscaRapida(filter.buscaGlobal(), predicates, cb,
                        List.<Expression<String>>of(joinBusca.<String>get("numSerie"), root.<String>get("ocorrencia")));
                query.distinct(true);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Predicate predicadoDaEtapa(
            EtapaSolicitacaoTecnica etapa,
            jakarta.persistence.criteria.Root<SolicitacaoMaterial> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb
    ) {
        Predicate contextoTecnico = cb.equal(root.get("contexto"), ContextoSolicitacaoMaterial.TECNICO);
        Predicate aberta = cb.isNull(root.get("encerradaEm"));

        return switch (etapa) {
            case AGUARDANDO_PLANEJAMENTO -> cb.and(
                    contextoTecnico,
                    tipoAdicao(root),
                    cb.equal(root.get("status"), SolicitacaoMaterial.StatusSolicitacao.APROVADO),
                    aberta,
                    cb.isNull(root.get("dataPrevistaRetirada")),
                    cb.or(
                            cb.equal(root.get("statusSeparacao"), StatusSeparacao.AGUARDANDO_AGENDAMENTO),
                            cb.isNull(root.get("statusSeparacao"))));
            case EM_SEPARACAO -> cb.and(
                    contextoTecnico,
                    tipoAdicao(root),
                    cb.or(
                            cb.equal(root.get("statusSeparacao"), StatusSeparacao.EM_SEPARACAO),
                            cb.and(
                                    cb.equal(root.get("statusSeparacao"), StatusSeparacao.PRONTO_PARA_SEPARACAO),
                                    cb.isNotNull(root.get("dataPrevistaRetirada")))),
                    aberta);
            case PENDENTE_ENTREGA -> {
                Join<SolicitacaoMaterial, ItemSolicitacao> itens = root.join("itens");
                query.distinct(true);
                Predicate condicaoAdicao = cb.and(
                        tipoAdicao(root),
                        cb.equal(root.get("statusSeparacao"), StatusSeparacao.SEPARADO),
                        root.get("status").in(
                                SolicitacaoMaterial.StatusSolicitacao.APROVADO,
                                SolicitacaoMaterial.StatusSolicitacao.ENTREGUE_PARCIAL,
                                SolicitacaoMaterial.StatusSolicitacao.ESTORNADO),
                        quantidadePendentePositiva(itens, cb));
                Predicate condicaoDevolucao = cb.and(
                        tipoDevolucao(root),
                        root.get("status").in(
                                SolicitacaoMaterial.StatusSolicitacao.APROVADO,
                                SolicitacaoMaterial.StatusSolicitacao.DEVOLVIDO_PARCIAL,
                                SolicitacaoMaterial.StatusSolicitacao.ESTORNADO),
                        quantidadeDevolucaoPendentePositiva(itens, cb));
                yield cb.and(
                        contextoTecnico,
                        aberta,
                        cb.or(condicaoAdicao, condicaoDevolucao));
            }
        };
    }

    private static Predicate tipoAdicao(
            jakarta.persistence.criteria.Root<SolicitacaoMaterial> root
    ) {
        return root.get("tipoTecnico").in(
                TipoSolicitacaoTecnica.ADICAO_COMUM,
                TipoSolicitacaoTecnica.ADICAO_CONCRETO);
    }

    private static Predicate tipoDevolucao(
            jakarta.persistence.criteria.Root<SolicitacaoMaterial> root
    ) {
        return root.get("tipoTecnico").in(
                TipoSolicitacaoTecnica.DEVOLUCAO_COMUM,
                TipoSolicitacaoTecnica.DEVOLUCAO_CONCRETO);
    }

    private static Predicate quantidadePendentePositiva(
            Join<SolicitacaoMaterial, ItemSolicitacao> item,
            jakarta.persistence.criteria.CriteriaBuilder cb
    ) {
        Expression<BigDecimal> quantidade = item.<BigDecimal>get("quantidade");
        Expression<BigDecimal> entregaBruta = cb.coalesce(item.<BigDecimal>get("quantidadeEntregueBruta"), BigDecimal.ZERO);
        Expression<BigDecimal> entregaEstornada = cb.coalesce(item.<BigDecimal>get("quantidadeEntregaEstornada"), BigDecimal.ZERO);
        Expression<BigDecimal> pendente = cb.diff(cb.sum(quantidade, entregaEstornada), entregaBruta);
        return cb.greaterThan(pendente, BigDecimal.ZERO);
    }

    private static Predicate quantidadeDevolucaoPendentePositiva(
            Join<SolicitacaoMaterial, ItemSolicitacao> item,
            jakarta.persistence.criteria.CriteriaBuilder cb
    ) {
        Expression<BigDecimal> quantidade = item.<BigDecimal>get("quantidade");
        Expression<BigDecimal> devolvidaBruta = cb.coalesce(item.<BigDecimal>get("quantidadeDevolvidaBruta"), BigDecimal.ZERO);
        Expression<BigDecimal> devolucaoEstornada = cb.coalesce(item.<BigDecimal>get("quantidadeDevolucaoEstornada"), BigDecimal.ZERO);
        Expression<BigDecimal> pendente = cb.diff(cb.sum(quantidade, devolucaoEstornada), devolvidaBruta);
        return cb.greaterThan(pendente, BigDecimal.ZERO);
    }

    private static BigInteger numeroReserva(String valor) {
        try {
            return new BigInteger(valor.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Numero de reserva deve conter apenas digitos.", ex);
        }
    }
}
