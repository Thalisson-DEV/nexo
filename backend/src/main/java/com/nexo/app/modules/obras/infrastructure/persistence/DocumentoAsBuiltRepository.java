package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.domain.DocumentoAsBuilt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentoAsBuiltRepository extends JpaRepository<DocumentoAsBuilt, UUID> {

    List<DocumentoAsBuilt> findByIdObraAndAtivoTrueOrderByDataCriacaoDesc(UUID idObra);

    /** Tentativa que falhou não consome o número da versão. */
    @Query("""
            select coalesce(max(d.numeroVersao), 0) from DocumentoAsBuilt d
            where d.idObra = :idObra and d.status <> com.nexo.app.modules.obras.domain.DocumentoAsBuilt.Status.ERRO
            """)
    int ultimaVersao(@Param("idObra") UUID idObra);
}
