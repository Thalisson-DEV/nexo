package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.domain.EventoInspecaoSeguranca;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventoInspecaoSegurancaRepository extends JpaRepository<EventoInspecaoSeguranca, UUID> {

    List<EventoInspecaoSeguranca> findAllByIdInspecaoOrderBySequenciaAsc(UUID idInspecao);

    Optional<EventoInspecaoSeguranca> findTopByIdInspecaoOrderBySequenciaDesc(UUID idInspecao);

    Page<EventoInspecaoSeguranca> findAllByIdInspecao(UUID idInspecao, Pageable pageable);
}
