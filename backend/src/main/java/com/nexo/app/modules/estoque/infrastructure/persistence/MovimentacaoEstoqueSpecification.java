package com.nexo.app.modules.estoque.infrastructure.persistence;

import com.nexo.app.modules.estoque.application.dto.MovimentacaoEstoqueFilter;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.MovimentacaoEstoque;
import com.nexo.app.modules.estoque.domain.NormalizadorOcorrenciaTecnica;
import com.nexo.app.modules.estoque.domain.ReservaSolicitacaoTecnica;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.shared.infrastructure.persistence.ReferenciaUtils;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class MovimentacaoEstoqueSpecification {

    private MovimentacaoEstoqueSpecification() {}

    public static Specification<MovimentacaoEstoque> filterBy(MovimentacaoEstoqueFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            if (filter.idEstoque() != null) {
                predicates.add(cb.or(
                    cb.equal(root.get("estoqueOrigem").get("id"), filter.idEstoque()),
                    cb.equal(root.get("estoqueDestino").get("id"), filter.idEstoque())
                ));
            }

            if (filter.idBaseOrigem() != null) {
                Join<MovimentacaoEstoque, Estoque> origem = root.join("estoqueOrigem", JoinType.LEFT);
                predicates.add(cb.equal(origem.get("idBaseOperacional"), filter.idBaseOrigem()));
                query.distinct(true);
            }

            if (filter.idBaseDestino() != null) {
                Join<MovimentacaoEstoque, Estoque> destino = root.join("estoqueDestino", JoinType.LEFT);
                predicates.add(cb.equal(destino.get("idBaseOperacional"), filter.idBaseDestino()));
                query.distinct(true);
            }

            if (filter.idMaterial() != null) {
                predicates.add(cb.equal(root.get("material").get("id"), filter.idMaterial()));
            }

            if (filter.condicao() != null) {
                predicates.add(cb.equal(root.get("condicao"), filter.condicao()));
            }

            if (filter.tipo() != null) {
                predicates.add(cb.equal(root.get("tipo"), filter.tipo()));
            }

            // Filtro por Referência (Mês/Ano)
            OffsetDateTime inicioMes = ReferenciaUtils.converterParaDataInicio(filter.referencia());
            OffsetDateTime fimMes = ReferenciaUtils.converterParaDataFim(filter.referencia());
            if (inicioMes != null && fimMes != null) {
                predicates.add(cb.between(root.get("dataHora"), inicioMes, fimMes));
            }

            if (filter.dataInicio() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dataHora"), filter.dataInicio()));
            }

            if (filter.dataFim() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dataHora"), filter.dataFim()));
            }

            if (filter.idUsuarioOperador() != null) {
                predicates.add(cb.equal(root.get("idUsuarioOperador"), filter.idUsuarioOperador()));
            }

            if (filter.numSerie() != null && !filter.numSerie().isBlank()) {
                String searchPattern = "%" + filter.numSerie().trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("numSerie").as(String.class)), searchPattern));
            }

            Join<MovimentacaoEstoque, SolicitacaoMaterial> solicitacao = null;
            if (filter.contexto() != null || filter.statusSolicitacao() != null
                    || filter.statusSeparacao() != null) {
                solicitacao = root.join("solicitacao", JoinType.LEFT);
                query.distinct(true);
            }

            if (filter.contexto() != null) {
                predicates.add(cb.equal(solicitacao.get("contexto"), filter.contexto()));
            }

            if (filter.idSolicitacao() != null) {
                predicates.add(cb.equal(root.get("solicitacao").get("id"), filter.idSolicitacao()));
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
                predicates.add(cb.equal(root.get("ocorrencia"),
                        NormalizadorOcorrenciaTecnica.normalizar(filter.ocorrencia())));
            }

            if (filter.idEquipe() != null) {
                predicates.add(cb.equal(root.get("idEquipe"), filter.idEquipe()));
            }

            Join<MovimentacaoEstoque, ReservaSolicitacaoTecnica> reserva = null;
            if (filter.numeroReserva() != null && !filter.numeroReserva().isBlank()
                    || filter.idAgenteReserva() != null) {
                reserva = root.join("reserva", JoinType.LEFT);
                query.distinct(true);
            }

            if (filter.idReserva() != null) {
                predicates.add(cb.equal(root.get("reserva").get("id"), filter.idReserva()));
            }

            if (filter.numeroReserva() != null && !filter.numeroReserva().isBlank()) {
                predicates.add(cb.equal(reserva.get("numero"), numeroReserva(filter.numeroReserva())));
            }

            if (filter.idAgenteReserva() != null) {
                predicates.add(cb.equal(reserva.get("idAgenteResponsavel"), filter.idAgenteReserva()));
            }

            if (filter.statusSolicitacao() != null) {
                predicates.add(cb.equal(solicitacao.get("status"), filter.statusSolicitacao()));
            }

            if (filter.statusSeparacao() != null) {
                predicates.add(cb.equal(solicitacao.get("statusSeparacao"), filter.statusSeparacao()));
            }

            // Busca rápida por série, material ou ocorrência técnica.
            if (filter.buscaGlobal() != null && !filter.buscaGlobal().isBlank()) {
                Join<MovimentacaoEstoque, Object> joinMaterial = root.join("material", JoinType.INNER);
                SpecificationUtils.buscaRapida(filter.buscaGlobal(), predicates, cb, List.<Expression<String>>of(
                        joinMaterial.<String>get("codigo"), joinMaterial.<String>get("descricao"),
                        root.<String>get("numSerie"), root.<String>get("ocorrencia")));
                query.distinct(true);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static BigInteger numeroReserva(String valor) {
        String normalizado = valor.trim();
        if (!normalizado.matches("\\d+")) {
            throw new IllegalArgumentException("Numero de reserva deve conter apenas digitos.");
        }
        return new BigInteger(normalizado);
    }
}
