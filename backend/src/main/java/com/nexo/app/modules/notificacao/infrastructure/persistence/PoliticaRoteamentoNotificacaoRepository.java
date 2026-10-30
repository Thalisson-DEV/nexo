package com.nexo.app.modules.notificacao.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nexo.app.modules.notificacao.domain.PoliticaRoteamentoNotificacao;

public interface PoliticaRoteamentoNotificacaoRepository
        extends JpaRepository<PoliticaRoteamentoNotificacao, UUID> {

    List<PoliticaRoteamentoNotificacao> findAllByOrderByNomeAsc();

    boolean existsByNomeIgnoreCase(String nome);

    boolean existsByNomeIgnoreCaseAndIdNot(String nome, UUID id);
}
