package com.nexo.app.modules.rh;

import java.util.Optional;
import java.util.UUID;

public interface CoordenadorApi {
    boolean existeCoordenador(UUID id);
    Optional<CoordenadorResponse> buscarPorIdResponse(UUID id);
    Optional<UUID> buscarIdPorNomeColaborador(String nome);
}
