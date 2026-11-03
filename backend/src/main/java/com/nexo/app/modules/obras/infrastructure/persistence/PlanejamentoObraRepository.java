package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.domain.PlanejamentoObra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanejamentoObraRepository extends JpaRepository<PlanejamentoObra, UUID> {

    Optional<PlanejamentoObra> findByIdObraAndAtivoTrue(UUID idObra);

    List<PlanejamentoObra> findByIdObraInAndAtivoTrue(Collection<UUID> idsObras);

    @Query("""
            select p.idObra from PlanejamentoObra p where p.ativo = true and p.levantamentoConcluidoEm is not null
            """)
    List<UUID> obrasComLevantamentoConcluido();
}
