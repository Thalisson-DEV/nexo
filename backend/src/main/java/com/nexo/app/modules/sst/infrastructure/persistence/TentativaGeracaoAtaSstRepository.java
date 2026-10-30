package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.domain.TentativaGeracaoAtaSst;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TentativaGeracaoAtaSstRepository extends JpaRepository<TentativaGeracaoAtaSst, UUID> {
    Optional<TentativaGeracaoAtaSst> findByIdReuniaoAndClientRequestId(UUID idReuniao, UUID clientRequestId);
}
