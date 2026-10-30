package com.nexo.app.modules.frota.infrastructure.persistence;

import com.nexo.app.modules.frota.domain.Implemento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ImplementoRepository extends JpaRepository<Implemento, UUID>, JpaSpecificationExecutor<Implemento> {

    boolean existsByIdentificacaoIgnoreCase(String identificacao);

    boolean existsByIdentificacaoIgnoreCaseAndIdNot(String identificacao, UUID id);

    List<Implemento> findAllByIdBaseOperacionalIn(Collection<UUID> idsBase);
}
