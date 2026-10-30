package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.domain.ModeloChecklistGovernancaSst;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ModeloChecklistGovernancaSstRepository extends
        JpaRepository<ModeloChecklistGovernancaSst, UUID>,
        JpaSpecificationExecutor<ModeloChecklistGovernancaSst> {
}
