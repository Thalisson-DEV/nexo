package com.nexo.app.modules.execucao.infrastructure.persistence;

import com.nexo.app.modules.execucao.application.dto.ExecucaoFilter;
import com.nexo.app.modules.execucao.domain.ExecucaoAcao;
import com.nexo.app.modules.execucao.domain.ExecucaoComercial;
import com.nexo.app.modules.execucao.domain.ExecucaoMaterial;
import com.nexo.app.modules.execucao.domain.TipoNota;
import com.nexo.app.shared.infrastructure.persistence.ReferenciaUtils;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import com.nexo.app.shared.infrastructure.security.HashUtils;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Specification para filtros dinâmicos de Execução Comercial.
 * Suporta filtros por cabeçalho, ações e materiais (incluindo rastreabilidade por série).
 */
public class ExecucaoSpecification {

    private static final ZoneId FUSO_OPERACAO = ZoneId.of("America/Sao_Paulo");

    private ExecucaoSpecification() {}

    public static Specification<ExecucaoComercial> filterBy(ExecucaoFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter == null) {
                return cb.conjunction();
            }

            // 1. Filtros de Cabeçalho (ExecucaoComercial)
            if (StringUtils.hasText(filter.referenciaNota())) {
                String hash = HashUtils.gerarBlindIndex(filter.referenciaNota());
                predicates.add(cb.equal(root.get("referenciaNotaHash"), hash));
            }

            if (filter.idEquipe() != null) {
                predicates.add(cb.equal(root.get("idEquipe"), filter.idEquipe()));
            }

            if (filter.idTurno() != null) {
                predicates.add(cb.equal(root.get("idTurno"), filter.idTurno()));
            }

            if (filter.idTipoServico() != null) {
                predicates.add(cb.equal(root.get("idTipoServico"), filter.idTipoServico()));
            } else if (Boolean.TRUE.equals(filter.semTipoServico())) {
                predicates.add(cb.isNull(root.get("idTipoServico")));
            }

            if (filter.tipoNota() != null) {
                predicates.add(cb.equal(root.get("tipoNota"), filter.tipoNota()));
            }

            if (Boolean.TRUE.equals(filter.aguardandoNumeroOficial())) {
                predicates.add(cb.equal(root.get("tipoNota"), TipoNota.AVULSA));
                predicates.add(cb.isNull(root.get("dataNumeroOficial")));
            }

            if (Boolean.TRUE.equals(filter.alterada())) {
                predicates.add(cb.greaterThan(root.get("totalAlteracoes"), 0));
            }

            if (filter.idMunicipio() != null) {
                predicates.add(cb.equal(root.get("idMunicipio"), filter.idMunicipio()));
            }

            // Filtro por Referência (Mês/Ano)
            OffsetDateTime inicioMes = ReferenciaUtils.converterParaDataInicio(filter.referencia());
            OffsetDateTime fimMes = ReferenciaUtils.converterParaDataFim(filter.referencia());
            if (inicioMes != null && fimMes != null) {
                predicates.add(cb.between(root.get("dataLancamento"), inicioMes, fimMes));
            }


            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("statusExecucao"), filter.status()));
            }

            // Filtro por Hoje (dia civil da operação, no fuso de São Paulo)
            if (Boolean.TRUE.equals(filter.apenasHoje())) {
                ZonedDateTime inicioHoje = LocalDate.now(FUSO_OPERACAO).atStartOfDay(FUSO_OPERACAO);
                predicates.add(cb.greaterThanOrEqualTo(root.get("dataLancamento"), inicioHoje.toOffsetDateTime()));
                predicates.add(cb.lessThan(root.get("dataLancamento"), inicioHoje.plusDays(1).toOffsetDateTime()));
            }

            // Intervalo da data de lançamento
            if (filter.dataLancamentoDe() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dataLancamento"), filter.dataLancamentoDe()));
            }
            if (filter.dataLancamentoAte() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dataLancamento"), filter.dataLancamentoAte()));
            }

            // 2. Filtros de Ações (Join com ExecucaoAcao)
            if (filter.idAcao() != null) {
                Join<ExecucaoComercial, ExecucaoAcao> joinAcoes = root.join("acoes");
                predicates.add(cb.equal(joinAcoes.get("idAcao"), filter.idAcao()));
            }

            // 3. Filtros de Materiais (Join com ExecucaoMaterial)
            if (filter.idMaterial() != null || filter.operacaoMaterial() != null || StringUtils.hasText(filter.numSerieMaterial())) {
                Join<ExecucaoComercial, ExecucaoMaterial> joinMateriais = root.join("materiais");
                
                if (filter.idMaterial() != null) {
                    predicates.add(cb.equal(joinMateriais.get("idMaterial"), filter.idMaterial()));
                }
                
                if (filter.operacaoMaterial() != null) {
                    predicates.add(cb.equal(joinMateriais.get("operacao"), filter.operacaoMaterial()));
                }

                if (StringUtils.hasText(filter.numSerieMaterial())) {
                    predicates.add(cb.like(cb.upper(joinMateriais.get("numSerie")), "%" + filter.numSerieMaterial().toUpperCase() + "%"));
                }
            }
            SpecificationUtils.buscaRapida(filter.buscaGlobal(), predicates, cb, root);

            query.distinct(true);

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
