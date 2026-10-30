package com.nexo.app.modules.notificacao.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexo.app.modules.notificacao.domain.RegraNotificacao;

public interface RegraNotificacaoRepository
        extends JpaRepository<RegraNotificacao, UUID>, JpaSpecificationExecutor<RegraNotificacao> {

    java.util.Optional<RegraNotificacao> findFirstByAtivoTrueOrderByIdAsc();

    /*
     * As duas consultas abaixo devolvem identificadores, e não entidades, porque cada
     * regra é aplicada na sua própria transação — quem seleciona não é quem usa. Trazer
     * a entidade aqui a entregaria desanexada, e as coleções de condições e
     * destinatários estourariam LazyInitializationException do outro lado.
     */

    @Query("SELECT r.id FROM RegraNotificacao r WHERE r.chaveGatilho = :chave AND r.ativo = true")
    List<UUID> buscarIdsAtivosPorGatilho(@Param("chave") String chaveGatilho);

    /**
     * Regras completas para projeções internas. A coleção de condições e
     * destinatários permanece lazy e é consumida dentro da transação do caso de uso.
     */
    List<RegraNotificacao> findAllByChaveGatilhoAndAtivoTrueOrderByIdAsc(String chaveGatilho);

    /** Inclui regra desativada para que o snapshot possa representar a política vazia. */
    List<RegraNotificacao> findAllByChaveGatilhoOrderByIdAsc(String chaveGatilho);

    @Query("""
            SELECT r.id AS id, r.chaveGatilho AS chaveGatilho FROM RegraNotificacao r
             WHERE r.chaveGatilho IN :chaves AND r.ativo = true
            """)
    List<RegraPorGatilho> buscarAtivasPorGatilhos(@Param("chaves") List<String> chavesGatilho);

    /** Projeção da varredura de limiares: só o necessário para medir e delegar. */
    interface RegraPorGatilho {
        UUID getId();

        String getChaveGatilho();
    }

    boolean existsByNomeIgnoreCaseAndIdNot(String nome, UUID id);

    boolean existsByNomeIgnoreCase(String nome);

    List<RegraNotificacao> findAllByIdAutomacaoIsNullOrderByDataCriacaoAsc();
}
