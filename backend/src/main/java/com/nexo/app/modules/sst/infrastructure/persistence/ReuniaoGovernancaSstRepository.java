package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.domain.ReuniaoGovernancaSst;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReuniaoGovernancaSstRepository
        extends JpaRepository<ReuniaoGovernancaSst, UUID>, JpaSpecificationExecutor<ReuniaoGovernancaSst> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReuniaoGovernancaSst r where r.id = :id and r.ativo = true")
    Optional<ReuniaoGovernancaSst> findAtivaByIdForUpdate(@Param("id") UUID id);

    Optional<ReuniaoGovernancaSst> findByIdAndAtivoTrue(UUID id);
}
