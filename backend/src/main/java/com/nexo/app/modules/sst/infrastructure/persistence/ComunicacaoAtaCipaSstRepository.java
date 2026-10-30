package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.domain.ComunicacaoAtaCipaSst;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ComunicacaoAtaCipaSstRepository extends JpaRepository<ComunicacaoAtaCipaSst, UUID> {
    List<ComunicacaoAtaCipaSst> findAllByIdReuniaoAndAtivoTrueOrderByComunicadaEmDesc(UUID idReuniao);
    Optional<ComunicacaoAtaCipaSst> findByIdAndIdReuniaoAndAtivoTrue(UUID id, UUID idReuniao);
}
