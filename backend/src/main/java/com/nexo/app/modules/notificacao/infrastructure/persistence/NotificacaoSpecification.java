package com.nexo.app.modules.notificacao.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.nexo.app.modules.notificacao.application.dto.NotificacaoFilter;
import com.nexo.app.modules.notificacao.domain.Notificacao;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

public final class NotificacaoSpecification {

    private NotificacaoSpecification() {}

    /**
     * O dono da caixa vem por parâmetro, não pelo filtro, para que nenhuma
     * combinação de query string consiga ampliar o escopo além do próprio usuário.
     */
    public static Specification<Notificacao> daCaixaDe(UUID idUsuario, NotificacaoFilter filtro) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("idUsuario"), idUsuario));
            predicates.add(cb.isTrue(root.get("ativo")));

            // Expirada não é apagada — apenas deixa de aparecer, para que o histórico
            // e a auditoria continuem íntegros.
            OffsetDateTime agora = OffsetDateTime.now();
            predicates.add(cb.or(
                    cb.isNull(root.get("expiraEm")),
                    cb.greaterThan(root.get("expiraEm"), agora)));

            boolean incluirArquivadas = filtro != null && Boolean.TRUE.equals(filtro.incluirArquivadas());
            if (!incluirArquivadas) {
                predicates.add(cb.isNull(root.get("arquivadaEm")));
            }

            if (filtro != null) {
                List<Expression<String>> busca = List.of(root.get("titulo"), root.get("corpo"));
                SpecificationUtils.buscaRapida(filtro.buscaGlobal(), predicates, cb, busca);

                if (filtro.categoria() != null) {
                    predicates.add(cb.equal(root.get("categoria"), filtro.categoria()));
                }
                if (filtro.prioridade() != null) {
                    predicates.add(cb.equal(root.get("prioridade"), filtro.prioridade()));
                }
                if (Boolean.TRUE.equals(filtro.apenasNaoLidas())) {
                    predicates.add(cb.isNull(root.get("lidaEm")));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
