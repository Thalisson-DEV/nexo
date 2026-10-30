package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.domain.EvidenciaFotograficaReuniaoSst;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EvidenciaFotograficaReuniaoSstRepository
        extends JpaRepository<EvidenciaFotograficaReuniaoSst, UUID> {
    List<EvidenciaFotograficaReuniaoSst> findAllByIdReuniaoAndAtivoTrueOrderByEnviadaEmDesc(UUID idReuniao);
    Optional<EvidenciaFotograficaReuniaoSst> findByIdAndIdReuniaoAndAtivoTrue(UUID id, UUID idReuniao);
}
