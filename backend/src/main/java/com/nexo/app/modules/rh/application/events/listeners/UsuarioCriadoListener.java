package com.nexo.app.modules.rh.application.events.listeners;

import com.nexo.app.shared.domain.events.UsuarioCriadoParaColaboradorEvent;
import com.nexo.app.modules.rh.domain.Colaborador;
import com.nexo.app.modules.rh.infrastructure.persistence.ColaboradorRepository;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Escuta eventos do módulo IAM para processar a confirmação de criação
 * de usuários e vincular o ID ao colaborador correspondente no RH.
 */
@Component
@RequiredArgsConstructor
public class UsuarioCriadoListener {

    private final ColaboradorRepository colaboradorRepository;

    /**
     * Grava direto no repositório, fora do {@code ColaboradorUseCases}, então precisa
     * derrubar o cache de colaboradores por conta própria — do contrário as listagens
     * continuam servindo o colaborador com {@code idUsuario} nulo.
     */
    @ApplicationModuleListener
    @CacheEvict(cacheNames = "colaboradores", allEntries = true)
    public void on(UsuarioCriadoParaColaboradorEvent event) {
        Colaborador colaborador = colaboradorRepository.findById(event.colaboradorId())
            .orElseThrow(() -> new RecursoNaoEncontradoException("Colaborador não encontrado para vinculação: " + event.colaboradorId()));

        // Vincula o ID do usuário criado no IAM ao registro do colaborador no RH
        colaborador.vincularUsuario(event.usuarioId());

        colaboradorRepository.save(colaborador);
    }
}
