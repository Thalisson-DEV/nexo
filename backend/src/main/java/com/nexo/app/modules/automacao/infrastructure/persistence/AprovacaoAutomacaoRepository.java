package com.nexo.app.modules.automacao.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexo.app.modules.automacao.domain.AprovacaoAutomacao;

public interface AprovacaoAutomacaoRepository extends JpaRepository<AprovacaoAutomacao, UUID> {

    List<AprovacaoAutomacao> findByIdExecucao(UUID idExecucao);

    /** Pedidos pendentes em que o usuário é aprovador (a lista é JSON de ids em texto). */
    @Query(value = """
            SELECT * FROM automacao_aprovacoes
             WHERE status = 'PENDENTE' AND aprovadores @> to_jsonb(CAST(:usuario AS text))
             ORDER BY criada_em DESC
            """, nativeQuery = true)
    List<AprovacaoAutomacao> buscarPendentesDe(@Param("usuario") String usuarioId);

    List<AprovacaoAutomacao> findByStatusOrderByCriadaEmDesc(String status);
}
