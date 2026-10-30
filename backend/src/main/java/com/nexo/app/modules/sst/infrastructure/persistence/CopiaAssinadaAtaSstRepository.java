package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.domain.CopiaAssinadaAtaSst;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CopiaAssinadaAtaSstRepository extends JpaRepository<CopiaAssinadaAtaSst, UUID> {
    Optional<CopiaAssinadaAtaSst> findByIdReuniaoAndIdVersaoAtaAndAtivoTrue(UUID idReuniao, UUID idVersaoAta);
    boolean existsByIdReuniaoAndIdVersaoAtaAndAtivoTrue(UUID idReuniao, UUID idVersaoAta);
}
