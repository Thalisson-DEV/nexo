package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.domain.MembroCorpoSst;
import com.nexo.app.modules.sst.domain.OrigemMembroCipa;
import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MembroCorpoSstRepository extends JpaRepository<MembroCorpoSst, UUID> {

    Optional<MembroCorpoSst> findByIdAndCorpoIdAndAtivoTrue(UUID id, UUID idCorpo);

    boolean existsByCorpoIdAndIdColaboradorAndFimParticipacaoIsNullAndAtivoTrue(
            UUID idCorpo, UUID idColaborador);

    /** Contrato compartilhado com reuniões: uma consulta, ownership pelo corpo e data efetiva. */
    @Query("""
            select membro from MembroCorpoSst membro
            where membro.corpo.id = :idCorpo
              and membro.ativo = true
              and membro.inicioParticipacao <= :dataReferencia
              and (membro.fimParticipacao is null or membro.fimParticipacao > :dataReferencia)
            order by membro.papel, membro.id
            """)
    List<MembroCorpoSst> buscarAtivosNoCorpoEm(
            @Param("idCorpo") UUID idCorpo,
            @Param("dataReferencia") LocalDate dataReferencia);

    @Query("""
            select membro from MembroCorpoSst membro
            join fetch membro.corpo corpo
            where corpo.tipo in :tipos
              and membro.papel in :papeis
              and corpo.status = com.nexo.app.modules.sst.domain.StatusCorpoGovernancaSst.ATIVO
              and corpo.ativo = true
              and membro.ativo = true
              and corpo.inicioPeriodo <= :dataReferencia
              and corpo.fimPeriodo >= :dataReferencia
              and membro.inicioParticipacao <= :dataReferencia
              and (membro.fimParticipacao is null or membro.fimParticipacao > :dataReferencia)
            order by corpo.id, membro.papel, membro.id
            """)
    List<MembroCorpoSst> buscarAtivosPorTipoEPapelEm(
            @Param("tipos") Collection<TipoCorpoGovernancaSst> tipos,
            @Param("papeis") Collection<com.nexo.app.modules.sst.domain.PapelMembroGovernancaSst> papeis,
            @Param("dataReferencia") LocalDate dataReferencia);

    @Query("""
            select membro from MembroCorpoSst membro
            where membro.idColaborador = :idColaborador
              and membro.corpo.tipo = :tipo
              and membro.origem = :origem
              and membro.inicioParticipacao < :inicioAtual
              and membro.ativo = true
            order by membro.inicioParticipacao desc, membro.id desc
            """)
    List<MembroCorpoSst> buscarHistoricoEleitoralAnterior(
            @Param("idColaborador") UUID idColaborador,
            @Param("tipo") TipoCorpoGovernancaSst tipo,
            @Param("origem") OrigemMembroCipa origem,
            @Param("inicioAtual") LocalDate inicioAtual,
            Pageable pageable);
}
