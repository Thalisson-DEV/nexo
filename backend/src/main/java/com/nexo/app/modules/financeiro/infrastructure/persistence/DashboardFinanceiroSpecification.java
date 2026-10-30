package com.nexo.app.modules.financeiro.infrastructure.persistence;

import com.nexo.app.modules.financeiro.application.dto.DashboardFinanceiroFilter;
import com.nexo.app.modules.financeiro.domain.SolicitacaoBase;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Escopo das solicitações que alimentam o dashboard financeiro.
 *
 * <p>A assinatura é genérica sobre {@link SolicitacaoBase} porque compra e
 * pagamento não são só parecidas: elas compartilham a mesma superclasse mapeada e,
 * portanto, exatamente os mesmos caminhos de atributo. Escrever duas
 * specifications idênticas seria criar duas cópias que passam a divergir na
 * primeira dimensão nova.</p>
 *
 * <p>O recorte temporal é sobre {@code dataCriacao} — a <b>abertura</b> da
 * solicitação, não a decisão. É quando a despesa foi proposta que define a qual
 * mês ela pertence, e essa data não se move quando o aprovador demora a responder.
 * A consequência é que o dashboard lê coortes, e não fluxo de caixa.</p>
 */
public final class DashboardFinanceiroSpecification {

    private DashboardFinanceiroSpecification() {
    }

    /**
     * @param inicio limite inferior inclusivo da data de abertura; {@code null} não restringe
     * @param fim    limite superior inclusivo da data de abertura; {@code null} não restringe
     */
    public static <T extends SolicitacaoBase> Specification<T> noEscopo(DashboardFinanceiroFilter filtro,
                                                                       OffsetDateTime inicio,
                                                                       OffsetDateTime fim) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("ativo")));

            if (inicio != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dataCriacao"), inicio));
            }
            if (fim != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dataCriacao"), fim));
            }

            if (filtro != null) {
                Path<?> nucleo = root.get("nucleo");

                SpecificationUtils.filtrarPor(filtro.idFormulario(), predicates,
                        root.get("formulario").get("id"));
                SpecificationUtils.filtrarPor(filtro.idSolicitante(), predicates,
                        root.get("usuarioSolicitanteId"));
                SpecificationUtils.filtrarPor(filtro.status(), predicates, root.get("status"));

                SpecificationUtils.filtrarPor(filtro.idBase(), predicates, nucleo.get("baseId"));
                SpecificationUtils.filtrarPor(filtro.idProcesso(), predicates, nucleo.get("processoId"));
                SpecificationUtils.filtrarPor(filtro.idObra(), predicates, nucleo.get("obraId"));
                SpecificationUtils.filtrarPor(filtro.idFornecedor(), predicates, nucleo.get("fornecedorId"));
                SpecificationUtils.filtrarPor(filtro.tipoDespesa(), predicates, nucleo.get("tipoDespesa"));
                SpecificationUtils.filtrarPor(filtro.prioridade(), predicates, nucleo.get("prioridade"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
