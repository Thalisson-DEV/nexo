package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.domain.ExigenciaTreinamentoSst;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExigenciaTreinamentoSstRepository extends JpaRepository<ExigenciaTreinamentoSst, UUID>,
        JpaSpecificationExecutor<ExigenciaTreinamentoSst> {

    Optional<ExigenciaTreinamentoSst> findByIdAndAtivoTrue(UUID id);

    List<ExigenciaTreinamentoSst> findAllByAtualTrueAndAtivoTrue();

    List<ExigenciaTreinamentoSst> findAllByIdTreinamentoInAndAtualTrueAndAtivoTrue(
            Collection<UUID> idsTreinamento);

    boolean existsByIdTreinamentoAndAtivoTrue(UUID idTreinamento);

    boolean existsByIdTreinamentoAndAtualTrueAndAtivoTrue(UUID idTreinamento);
}
