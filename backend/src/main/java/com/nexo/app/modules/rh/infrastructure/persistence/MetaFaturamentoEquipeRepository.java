package com.nexo.app.modules.rh.infrastructure.persistence;

import com.nexo.app.modules.rh.domain.MetaFaturamentoEquipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface MetaFaturamentoEquipeRepository
        extends JpaRepository<MetaFaturamentoEquipe, UUID>, JpaSpecificationExecutor<MetaFaturamentoEquipe> {

    List<MetaFaturamentoEquipe> findByAtivoTrueAndIdEquipe(UUID idEquipe);

    List<MetaFaturamentoEquipe> findByAtivoTrueAndIdEquipeIn(Collection<UUID> idsEquipes);
}
