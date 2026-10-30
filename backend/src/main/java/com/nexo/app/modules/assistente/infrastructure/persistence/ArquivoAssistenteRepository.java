package com.nexo.app.modules.assistente.infrastructure.persistence;

import com.nexo.app.modules.assistente.domain.ArquivoAssistente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArquivoAssistenteRepository extends JpaRepository<ArquivoAssistente, UUID> {

    Optional<ArquivoAssistente> findByIdAndUsuarioIdAndAtivoTrue(UUID id, UUID usuarioId);

    List<ArquivoAssistente> findByIdInAndUsuarioIdAndAtivoTrue(Collection<UUID> ids, UUID usuarioId);

    List<ArquivoAssistente> findByConversaIdAndAtivoTrueOrderByDataCriacaoAsc(UUID conversaId);

    Optional<ArquivoAssistente> findByConversaIdAndCodigoAndAtivoTrue(UUID conversaId, String codigo);

    @Query("select count(a) from ArquivoAssistente a where a.conversaId = :conversaId and a.codigo is not null")
    long contarCodificados(@Param("conversaId") UUID conversaId);
}
