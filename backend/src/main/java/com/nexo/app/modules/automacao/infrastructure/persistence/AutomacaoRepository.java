package com.nexo.app.modules.automacao.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexo.app.modules.automacao.domain.Automacao;

public interface AutomacaoRepository extends JpaRepository<Automacao, UUID>, JpaSpecificationExecutor<Automacao> {

    @Query("SELECT a FROM Automacao a WHERE a.ativo = true AND a.status = com.nexo.app.modules.automacao.domain.StatusAutomacao.ATIVA AND a.chaveEvento = :chave")
    List<Automacao> buscarAtivasPorEvento(@Param("chave") String chaveEvento);

    @Query("SELECT a FROM Automacao a WHERE a.ativo = true AND a.status = com.nexo.app.modules.automacao.domain.StatusAutomacao.ATIVA AND a.tipoGatilho = :tipo")
    List<Automacao> buscarAtivasPorGatilho(@Param("tipo") String tipoGatilho);

    @Query("SELECT a FROM Automacao a WHERE a.ativo = true AND a.status = com.nexo.app.modules.automacao.domain.StatusAutomacao.ATIVA AND a.tipoGatilho LIKE :prefixo")
    List<Automacao> buscarAtivasPorPrefixoGatilho(@Param("prefixo") String prefixo);

    Optional<Automacao> findByIdRegraOrigem(UUID idRegraOrigem);

    boolean existsByNomeIgnoreCaseAndAtivoTrue(String nome);

    /** Inclui as apagadas: o que o Nexo cria uma vez não volta depois de removido. */
    boolean existsByNome(String nome);

    List<Automacao> findByNomeAndAtivoTrue(String nome);
}
