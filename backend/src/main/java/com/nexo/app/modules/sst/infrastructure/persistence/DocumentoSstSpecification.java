package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.application.dto.DocumentoSstFilter;
import com.nexo.app.modules.sst.domain.DocumentoSst;
import com.nexo.app.modules.sst.domain.StatusValidadeDocumentoSst;
import com.nexo.app.shared.application.SelecaoMultipla;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class DocumentoSstSpecification {

    private DocumentoSstSpecification() {
    }

    public static Specification<DocumentoSst> comFiltros(DocumentoSstFilter filter,
                                                          LocalDate dataReferencia) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("ativo")));
            if (filter != null) {
                if (!SelecaoMultipla.vazia(filter.idBase())) {
                    predicates.add(root.get("idBase").in(filter.idBase()));
                }
                if (!SelecaoMultipla.vazia(filter.contexto())) {
                    predicates.add(root.get("contexto").in(filter.contexto()));
                }
                if (!SelecaoMultipla.vazia(filter.idCategoria())) {
                    predicates.add(root.get("idCategoria").in(filter.idCategoria()));
                }
                if (!SelecaoMultipla.vazia(filter.escopoTitular())) {
                    predicates.add(root.get("escopoTitular").in(filter.escopoTitular()));
                }
                if (!SelecaoMultipla.vazia(filter.idTitular())) {
                    predicates.add(root.get("idTitular").in(filter.idTitular()));
                }
                if (!SelecaoMultipla.vazia(filter.situacaoConferencia())) {
                    predicates.add(root.get("situacaoConferencia").in(filter.situacaoConferencia()));
                }
                if (filter.idColaborador() != null) {
                    predicates.add(cb.equal(root.get("idColaborador"), filter.idColaborador()));
                }
                if (filter.idTreinamento() != null) {
                    predicates.add(cb.equal(root.get("idTreinamento"), filter.idTreinamento()));
                }
                if (filter.atual() != null) {
                    predicates.add(cb.equal(root.get("atual"), filter.atual()));
                }
                if (filter.arquivado() != null) {
                    predicates.add(filter.arquivado()
                            ? cb.isNotNull(root.get("dataArquivamento"))
                            : cb.isNull(root.get("dataArquivamento")));
                }
                filtrarDatas(filter, dataReferencia, predicates, cb, root);
                SpecificationUtils.buscaRapida(filter.buscaGlobal(), predicates, cb,
                        List.<Expression<String>>of(root.get("categoriaCodigo"),
                                root.get("numeroDocumento"), root.get("emissor")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static void filtrarDatas(DocumentoSstFilter filter, LocalDate hoje,
                                     List<Predicate> predicates,
                                     jakarta.persistence.criteria.CriteriaBuilder cb,
                                     jakarta.persistence.criteria.Root<DocumentoSst> root) {
        if (filter.emissaoDe() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("dataEmissao"), filter.emissaoDe()));
        }
        if (filter.emissaoAte() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("dataEmissao"), filter.emissaoAte()));
        }
        if (filter.validadeDe() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("dataValidade"), filter.validadeDe()));
        }
        if (filter.validadeAte() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("dataValidade"), filter.validadeAte()));
        }
        if (filter.venceEmDias() != null) {
            int dias = Math.max(0, filter.venceEmDias());
            predicates.add(cb.between(root.get("dataValidade"), hoje, hoje.plusDays(dias)));
        }
        if (!SelecaoMultipla.vazia(filter.statusValidade())) {
            List<Predicate> alternativas = new ArrayList<>();
            for (StatusValidadeDocumentoSst status : filter.statusValidade()) {
                alternativas.add(switch (status) {
                    case SEM_VALIDADE -> cb.isNull(root.get("dataValidade"));
                    case VENCIDO -> cb.lessThan(root.get("dataValidade"), hoje);
                    case A_VENCER -> cb.and(
                            cb.greaterThanOrEqualTo(root.get("dataValidade"), hoje),
                            cb.lessThanOrEqualTo(root.get("dataInicioAlerta"), hoje));
                    case REGULAR -> cb.and(
                            cb.isNotNull(root.get("dataValidade")),
                            cb.greaterThanOrEqualTo(root.get("dataValidade"), hoje),
                            cb.or(cb.isNull(root.get("dataInicioAlerta")),
                                    cb.greaterThan(root.get("dataInicioAlerta"), hoje)));
                });
            }
            predicates.add(cb.or(alternativas.toArray(Predicate[]::new)));
        }
    }
}
