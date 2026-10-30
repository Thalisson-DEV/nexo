package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.domain.ParticipacaoReuniaoSst;
import com.nexo.app.modules.sst.domain.StatusPresencaReuniaoSst;
import com.nexo.app.modules.sst.domain.StatusReuniaoGovernancaSst;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParticipacaoReuniaoSstRepository extends JpaRepository<ParticipacaoReuniaoSst, UUID> {

    List<ParticipacaoReuniaoSst> findAllByIdReuniaoAndAtivoTrueOrderByNomeSnapshot(UUID idReuniao);

    Optional<ParticipacaoReuniaoSst> findByIdAndIdReuniaoAndAtivoTrue(UUID id, UUID idReuniao);

    boolean existsByIdReuniaoAndIdMembroCorpoAndAtivoTrue(UUID idReuniao, UUID idMembroCorpo);

    long countByIdReuniaoAndAtivoTrueAndPresenca(UUID idReuniao, StatusPresencaReuniaoSst presenca);

    @Query("""
            select count(p) from ParticipacaoReuniaoSst p, ReuniaoGovernancaSst r
             where p.idReuniao = r.id
               and r.idCorpo = :idCorpo
               and p.idMembroCorpo = :idMembro
               and p.titularidadeCipaSnapshot = 'TITULAR'
               and p.presenca = com.nexo.app.modules.sst.domain.StatusPresencaReuniaoSst.AUSENTE_NAO_JUSTIFICADA
               and r.tipoCorpo = com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst.CIPA
               and r.tipo = com.nexo.app.modules.sst.domain.TipoReuniaoGovernancaSst.ORDINARIA
               and r.status in :statusConcluidos
               and p.ativo = true and r.ativo = true
            """)
    long countFaltasCipaComputaveis(@Param("idCorpo") UUID idCorpo,
                                    @Param("idMembro") UUID idMembro,
                                    @Param("statusConcluidos") Collection<StatusReuniaoGovernancaSst> statusConcluidos);

    @Query("""
            select r.id from ParticipacaoReuniaoSst p, ReuniaoGovernancaSst r
             where p.idReuniao = r.id
               and r.idCorpo = :idCorpo
               and p.idMembroCorpo = :idMembro
               and p.titularidadeCipaSnapshot = 'TITULAR'
               and p.presenca = com.nexo.app.modules.sst.domain.StatusPresencaReuniaoSst.AUSENTE_NAO_JUSTIFICADA
               and r.tipoCorpo = com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst.CIPA
               and r.tipo = com.nexo.app.modules.sst.domain.TipoReuniaoGovernancaSst.ORDINARIA
               and r.status in :statusConcluidos
               and p.ativo = true and r.ativo = true
             order by r.dataHora
            """)
    List<UUID> findIdsReunioesFaltasCipaComputaveis(@Param("idCorpo") UUID idCorpo,
                                                     @Param("idMembro") UUID idMembro,
                                                     @Param("statusConcluidos") Collection<StatusReuniaoGovernancaSst> statusConcluidos);
}
