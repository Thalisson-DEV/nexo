package com.nexo.app.modules.rh;

import java.util.Optional;
import java.util.UUID;

public interface SupervisorApi {
    boolean existeSupervisor(UUID id);
    Optional<SupervisorResponse> buscarPorIdResponse(UUID id);
    Optional<UUID> buscarIdPorNomeColaborador(String nome);
}
