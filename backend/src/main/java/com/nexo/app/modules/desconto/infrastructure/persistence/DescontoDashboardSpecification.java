package com.nexo.app.modules.desconto.infrastructure.persistence;

import com.nexo.app.modules.desconto.application.dto.DashboardDescontoFilter;
import com.nexo.app.modules.desconto.domain.Desconto;
import com.nexo.app.modules.desconto.domain.DescontoItem;
import com.nexo.app.shared.application.SelecaoMultipla;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Escopo dos termos que alimentam o dashboard.
 *
 * <p>Duas escolhas explicam o formato desta classe:</p>
 *
 * <p><b>Os {@code fetch} não são otimização opcional.</b> O dashboard percorre os itens
 * de cada termo para somar valor por tipo de equipamento; sem trazê-los junto, um
 * período com 800 termos vira 1.601 consultas. Só uma coleção é buscada por consulta
 * ({@code itens}) — trazer {@code anexos} no mesmo {@code select} produziria o produto
 * cartesiano das duas listas, e o Hibernate recusa o segundo saco.</p>
 *
 * <p><b>O filtro de tipo de equipamento usa subconsulta, não o join buscado.</b>
 * Restringir o próprio {@code fetch} traria o termo com apenas parte dos itens dele, e
 * o valor total sairia menor do que é de verdade. A subconsulta seleciona <em>quais
 * termos</em> entram; o {@code fetch} continua trazendo todos os itens de cada um.</p>
 */
public final class DescontoDashboardSpecification {

    private DescontoDashboardSpecification() {
    }

    /**
     * As dimensões do <b>colaborador</b> (base, centro de custo e função) não entram
     * aqui de propósito: elas moram no cadastro de RH, do outro lado da fronteira do
     * módulo, e o dashboard já resolve a estrutura de quem aparece no período para
     * montar os rankings. Aplicar o recorte sobre esse mesmo mapa custa nada e evita
     * arrastar o esquema do RH para dentro de uma consulta de descontos.
     *
     * @param inicio limite inferior inclusivo da data de ocorrência; {@code null} não restringe
     * @param fim    limite superior inclusivo da data de ocorrência; {@code null} não restringe
     */
    public static Specification<Desconto> noEscopo(DashboardDescontoFilter filtro,
                                                   OffsetDateTime inicio,
                                                   OffsetDateTime fim) {
        return (root, query, cb) -> {
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                buscarItens(root);
                query.distinct(true);
            }

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("ativo")));

            if (inicio != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dataOcorrencia"), inicio));
            }
            if (fim != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dataOcorrencia"), fim));
            }
            if (filtro.inicioCriacao() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dataCriacao"), filtro.inicioCriacao()));
            }
            if (filtro.fimCriacao() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dataCriacao"), filtro.fimCriacao()));
            }

            if (!SelecaoMultipla.vazia(filtro.status())) {
                predicates.add(root.get("status").in(filtro.status()));
            }
            if (!SelecaoMultipla.vazia(filtro.idMotivo())) {
                predicates.add(root.get("motivo").get("id").in(filtro.idMotivo()));
            }
            if (!SelecaoMultipla.vazia(filtro.idColaborador())) {
                predicates.add(root.get("colaboradorId").in(filtro.idColaborador()));
            }
            if (!SelecaoMultipla.vazia(filtro.idUsuarioSolicitante())) {
                predicates.add(root.get("usuarioSolicitanteId").in(filtro.idUsuarioSolicitante()));
            }

            if (!SelecaoMultipla.vazia(filtro.idTipoEquipamento())) {
                Subquery<UUID> comTipo = query.subquery(UUID.class);
                Root<DescontoItem> item = comTipo.from(DescontoItem.class);
                comTipo.select(item.get("desconto").get("id"))
                        .where(cb.and(
                                cb.isTrue(item.get("ativo")),
                                item.get("tipoEquipamento").get("id").in(filtro.idTipoEquipamento())));
                predicates.add(root.get("id").in(comTipo));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static void buscarItens(Root<Desconto> root) {
        root.fetch("motivo", JoinType.LEFT);
        root.fetch("itens", JoinType.LEFT).fetch("tipoEquipamento", JoinType.LEFT);
    }
}
