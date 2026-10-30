package com.nexo.app.modules.chamado.infrastructure.persistence;

import com.nexo.app.modules.chamado.application.dto.ChamadoFilter;
import com.nexo.app.modules.chamado.domain.Chamado;
import com.nexo.app.modules.chamado.domain.StatusChamado;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ChamadoSpecification {

    private ChamadoSpecification() {}

    /** solicitanteVisivel torna a restrição de propriedade impossível de ser removida pelo cliente. */
    public static Specification<Chamado> comFiltros(ChamadoFilter filtro, UUID solicitanteVisivel) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (solicitanteVisivel != null) {
                predicates.add(cb.equal(root.get("usuarioSolicitanteId"), solicitanteVisivel));
            }

            if (filtro != null) {
                List<Expression<String>> camposBusca = List.of(
                        root.get("protocolo"), root.get("assunto"), root.get("descricao"));
                SpecificationUtils.buscaRapida(filtro.buscaGlobal(), predicates, cb, camposBusca);

                if (filtro.protocolo() != null && !filtro.protocolo().isBlank()) {
                    predicates.add(cb.equal(cb.upper(root.get("protocolo")), filtro.protocolo().trim().toUpperCase()));
                }
                if (filtro.topicoId() != null) {
                    predicates.add(cb.equal(root.get("topico").get("id"), filtro.topicoId()));
                }
                if (filtro.equipeId() != null) {
                    predicates.add(cb.equal(root.get("equipeAtendimento").get("id"), filtro.equipeId()));
                }
                if (filtro.prioridade() != null) {
                    predicates.add(cb.equal(root.get("prioridade"), filtro.prioridade()));
                }
                if (filtro.status() != null) {
                    predicates.add(cb.equal(root.get("status"), filtro.status()));
                }
                if (solicitanteVisivel == null && filtro.usuarioSolicitanteId() != null) {
                    predicates.add(cb.equal(root.get("usuarioSolicitanteId"), filtro.usuarioSolicitanteId()));
                }
                if (filtro.usuarioResponsavelId() != null) {
                    predicates.add(cb.equal(root.get("usuarioResponsavelId"), filtro.usuarioResponsavelId()));
                }
                if (filtro.dataAberturaInicio() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("dataAbertura"), filtro.dataAberturaInicio()));
                }
                if (filtro.dataAberturaFim() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("dataAbertura"), filtro.dataAberturaFim()));
                }
                if (filtro.slaPrimeiraRespostaVencido() != null) {
                    Predicate respondidoFora = cb.and(
                            cb.isNotNull(root.get("dataPrimeiraResposta")),
                            cb.greaterThan(root.get("dataPrimeiraResposta"), root.get("dataLimitePrimeiraResposta")));
                    Predicate finalizadoSemRespostaFora = cb.and(
                            cb.isNull(root.get("dataPrimeiraResposta")),
                            root.get("status").in(StatusChamado.FECHADO, StatusChamado.CANCELADO),
                            cb.greaterThan(root.get("dataFechamento"), root.get("dataLimitePrimeiraResposta")));
                    Predicate aindaSemRespostaFora = cb.and(
                            cb.isNull(root.get("dataPrimeiraResposta")),
                            cb.not(root.get("status").in(StatusChamado.FECHADO, StatusChamado.CANCELADO)),
                            cb.lessThan(root.get("dataLimitePrimeiraResposta"), OffsetDateTime.now()));
                    Predicate vencido = cb.or(respondidoFora, finalizadoSemRespostaFora, aindaSemRespostaFora);
                    predicates.add(Boolean.TRUE.equals(filtro.slaPrimeiraRespostaVencido())
                            ? vencido : cb.not(vencido));
                }
                if (filtro.slaResolucaoVencido() != null) {
                    Predicate resolvidoFora = cb.and(
                            cb.isNotNull(root.get("dataResolucao")),
                            cb.greaterThan(root.get("dataResolucao"), root.get("dataLimiteResolucao")));
                    Predicate finalizadoSemResolucaoFora = cb.and(
                            cb.isNull(root.get("dataResolucao")),
                            root.get("status").in(StatusChamado.FECHADO, StatusChamado.CANCELADO),
                            cb.greaterThan(root.get("dataFechamento"), root.get("dataLimiteResolucao")));
                    Predicate pausadoFora = cb.and(
                            cb.isNull(root.get("dataResolucao")),
                            cb.not(root.get("status").in(StatusChamado.FECHADO, StatusChamado.CANCELADO)),
                            cb.isNotNull(root.get("dataInicioPausaSla")),
                            cb.greaterThan(root.get("dataInicioPausaSla"), root.get("dataLimiteResolucao")));
                    Predicate emCursoFora = cb.and(
                            cb.isNull(root.get("dataResolucao")),
                            cb.not(root.get("status").in(StatusChamado.FECHADO, StatusChamado.CANCELADO)),
                            cb.isNull(root.get("dataInicioPausaSla")),
                            cb.lessThan(root.get("dataLimiteResolucao"), OffsetDateTime.now()));
                    Predicate vencido = cb.or(resolvidoFora, finalizadoSemResolucaoFora, pausadoFora, emCursoFora);
                    predicates.add(Boolean.TRUE.equals(filtro.slaResolucaoVencido())
                            ? vencido : cb.not(vencido));
                }
                predicates.add(cb.equal(root.get("ativo"), filtro.ativo() == null || filtro.ativo()));
            } else {
                predicates.add(cb.isTrue(root.get("ativo")));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
