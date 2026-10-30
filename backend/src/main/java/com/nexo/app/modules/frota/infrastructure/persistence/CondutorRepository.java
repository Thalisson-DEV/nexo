package com.nexo.app.modules.frota.infrastructure.persistence;

import com.nexo.app.modules.frota.domain.Condutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CondutorRepository extends JpaRepository<Condutor, UUID>, JpaSpecificationExecutor<Condutor> {

    Optional<Condutor> findByIdColaborador(UUID idColaborador);

    List<Condutor> findAllByIdColaboradorIn(Collection<UUID> idsColaborador);

    boolean existsByCnhNumeroHashAndIdColaboradorNot(String cnhNumeroHash, UUID idColaborador);

    List<Condutor> findAllByAtivoTrueAndCnhValidadeLessThanEqual(LocalDate limite);
}
