package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.domain.ConvidadoReuniaoSst;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConvidadoReuniaoSstRepository extends JpaRepository<ConvidadoReuniaoSst, UUID> {
    List<ConvidadoReuniaoSst> findAllByIdReuniaoAndAtivoTrueOrderByNomeSnapshot(UUID idReuniao);
    Optional<ConvidadoReuniaoSst> findByIdAndIdReuniaoAndAtivoTrue(UUID id, UUID idReuniao);
}
