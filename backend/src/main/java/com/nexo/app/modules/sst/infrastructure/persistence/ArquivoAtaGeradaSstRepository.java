package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.domain.ArquivoAtaGeradaSst;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArquivoAtaGeradaSstRepository extends JpaRepository<ArquivoAtaGeradaSst, UUID> {
    Optional<ArquivoAtaGeradaSst> findByIdAndIdReuniaoAndAtivoTrue(UUID id, UUID idReuniao);
    Optional<ArquivoAtaGeradaSst> findByIdVersaoAtaAndIdModeloAtaAndRevisaoModeloAtaAndAtivoTrue(
            UUID idVersaoAta, UUID idModeloAta, int revisaoModeloAta);
    List<ArquivoAtaGeradaSst> findAllByIdReuniaoAndAtivoTrueOrderByGeradoEmDesc(UUID idReuniao);
}
