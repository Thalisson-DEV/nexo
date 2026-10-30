package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.domain.ConstatacaoChecklistGovernancaSst;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ConstatacaoChecklistGovernancaSstRepository
        extends JpaRepository<ConstatacaoChecklistGovernancaSst, UUID> {
}
