package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.AgenteResponsavelApi;
import com.nexo.app.modules.obras.domain.AgenteResponsavel;
import com.nexo.app.modules.obras.infrastructure.persistence.AgenteResponsavelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgenteResponsavelApiAdapter implements AgenteResponsavelApi {

    private final AgenteResponsavelRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Optional<AgenteResponsavelPublico> buscarAtivoPorId(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return repository.findById(id)
                .filter(AgenteResponsavel::isAtivo)
                .map(agente -> new AgenteResponsavelPublico(
                        agente.getId(),
                        agente.getNome(),
                        agente.getEmail(),
                        agente.getTelefone(),
                        agente.getEmpresaOrgao()
                ));
    }
}
