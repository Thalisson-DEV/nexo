package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.domain.ImportacaoObra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ImportacaoObraRepository extends JpaRepository<ImportacaoObra, UUID> {

    Page<ImportacaoObra> findByTipoOrderByDataCriacaoDesc(ImportacaoObra.Tipo tipo, Pageable pageable);
}
