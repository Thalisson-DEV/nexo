package com.nexo.app.modules.assistente.infrastructure.persistence;

import com.nexo.app.modules.assistente.domain.ArquivoConteudo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ArquivoConteudoRepository extends JpaRepository<ArquivoConteudo, UUID> {
}
