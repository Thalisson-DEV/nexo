package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.domain.MetaInspecao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MetaInspecaoRepository
        extends JpaRepository<MetaInspecao, UUID>, JpaSpecificationExecutor<MetaInspecao> {

    /** Metas ativas do mesmo alvo — base da validação de sobreposição de vigência. */
    List<MetaInspecao> findByAtivoTrueAndIdFuncao(UUID idFuncao);

    List<MetaInspecao> findByAtivoTrueAndIdColaborador(UUID idColaborador);

    List<MetaInspecao> findByAtivoTrue();
}
