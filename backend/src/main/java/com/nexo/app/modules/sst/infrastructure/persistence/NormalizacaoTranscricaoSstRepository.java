package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.domain.NormalizacaoTranscricaoSst;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NormalizacaoTranscricaoSstRepository extends JpaRepository<NormalizacaoTranscricaoSst, UUID> {
    Optional<NormalizacaoTranscricaoSst> findByIdAndIdReuniaoAndAtivoTrue(UUID id, UUID idReuniao);
    Optional<NormalizacaoTranscricaoSst> findByIdReuniaoAndClientRequestIdAndAtivoTrue(
            UUID idReuniao, UUID clientRequestId);
    List<NormalizacaoTranscricaoSst> findAllByIdReuniaoAndAtivoTrueOrderByNumeroDesc(UUID idReuniao);
    Optional<NormalizacaoTranscricaoSst> findFirstByIdReuniaoAndConfirmadaEmIsNotNullAndAtivoTrueOrderByNumeroDesc(
            UUID idReuniao);

    @Query("select coalesce(max(n.numero), 0) + 1 from NormalizacaoTranscricaoSst n "
            + "where n.idReuniao = :idReuniao and n.ativo = true")
    int proximoNumero(@Param("idReuniao") UUID idReuniao);
}
