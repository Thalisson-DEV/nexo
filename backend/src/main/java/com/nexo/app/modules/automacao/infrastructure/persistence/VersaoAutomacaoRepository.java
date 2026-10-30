package com.nexo.app.modules.automacao.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nexo.app.modules.automacao.domain.VersaoAutomacao;

public interface VersaoAutomacaoRepository extends JpaRepository<VersaoAutomacao, UUID> {

    Optional<VersaoAutomacao> findByIdAutomacaoAndNumero(UUID idAutomacao, int numero);

    List<VersaoAutomacao> findByIdAutomacaoOrderByNumeroDesc(UUID idAutomacao);
}
