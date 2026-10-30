package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.domain.RevisaoTranscricaoSst;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RevisaoTranscricaoSstRepository extends JpaRepository<RevisaoTranscricaoSst, UUID> {
    List<RevisaoTranscricaoSst> findAllByIdReuniaoAndAtivoTrueOrderByNumeroDesc(UUID idReuniao);
    Optional<RevisaoTranscricaoSst> findByIdAndIdReuniaoAndAtivoTrue(UUID id, UUID idReuniao);
    Optional<RevisaoTranscricaoSst> findFirstByIdReuniaoAndConfirmadaEmIsNotNullAndAtivoTrueOrderByNumeroDesc(UUID idReuniao);
}
