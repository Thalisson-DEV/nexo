package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.domain.VersaoAtaSst;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VersaoAtaSstRepository extends JpaRepository<VersaoAtaSst, UUID> {
    List<VersaoAtaSst> findAllByIdReuniaoAndAtivoTrueOrderByNumeroDesc(UUID idReuniao);
    Optional<VersaoAtaSst> findByIdAndIdReuniaoAndAtivoTrue(UUID id, UUID idReuniao);
}
