package com.nexo.app.modules.obras;

import java.util.Optional;
import java.util.UUID;

/** Contrato público para resolução de agentes/fiscais responsáveis por reservas técnicas. */
public interface AgenteResponsavelApi {

    Optional<AgenteResponsavelPublico> buscarAtivoPorId(UUID id);

    record AgenteResponsavelPublico(
            UUID id,
            String nome,
            String email,
            String telefone,
            String empresaOrgao
    ) {
    }
}
