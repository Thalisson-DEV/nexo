package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.domain.ModeloAtaSst;
import com.nexo.app.modules.sst.domain.StatusModeloAtaSst;
import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ModeloAtaSstRepository extends JpaRepository<ModeloAtaSst, UUID> {

    Optional<ModeloAtaSst> findByIdAndAtivoTrue(UUID id);

    List<ModeloAtaSst> findAllByAtivoTrueOrderByNomeAscRevisaoDesc();

    @Query("""
            select distinct m from ModeloAtaSst m
             where m.tipoCorpo = :tipo
               and m.status = :status
               and m.ativo = true
               and (m.idBaseAplicavel is null or m.idBaseAplicavel = :idBase)
             order by m.idBaseAplicavel desc, m.nome, m.revisao desc
            """)
    List<ModeloAtaSst> buscarPublicadosAplicaveis(@Param("tipo") TipoCorpoGovernancaSst tipo,
                                                   @Param("status") StatusModeloAtaSst status,
                                                   @Param("idBase") UUID idBase);

    Optional<ModeloAtaSst> findFirstByFamiliaIdAndStatusAndAtivoTrueOrderByRevisaoDesc(
            UUID familiaId, StatusModeloAtaSst status);
}
