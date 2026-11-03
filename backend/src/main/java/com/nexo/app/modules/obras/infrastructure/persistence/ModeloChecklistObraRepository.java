package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.domain.ModeloChecklistObra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ModeloChecklistObraRepository
        extends JpaRepository<ModeloChecklistObra, UUID>, JpaSpecificationExecutor<ModeloChecklistObra> {

    boolean existsByFinalidadeAndTituloIgnoreCaseAndAtivoTrue(ModeloChecklistObra.Finalidade finalidade, String titulo);

    boolean existsByFinalidadeAndTituloIgnoreCaseAndAtivoTrueAndIdNot(ModeloChecklistObra.Finalidade finalidade, String titulo, UUID id);
}
