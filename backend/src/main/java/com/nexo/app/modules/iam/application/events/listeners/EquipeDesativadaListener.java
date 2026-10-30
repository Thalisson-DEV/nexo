package com.nexo.app.modules.iam.application.events.listeners;

import com.nexo.app.modules.iam.infrastructure.persistence.UsuarioRepository;
import com.nexo.app.shared.domain.events.EquipeDesativadaEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Escuta eventos de desativação de equipes vindos do módulo RH
 * para desativar o acesso (Usuario) correspondente no IAM.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EquipeDesativadaListener {

    private final UsuarioRepository usuarioRepository;

    @ApplicationModuleListener
    public void on(EquipeDesativadaEvent event) {
        log.info("Recebido evento de desativação da equipe {}. Desativando usuário correspondente {}", 
            event.equipeId(), event.usuarioId());

        usuarioRepository.findById(event.usuarioId()).ifPresentOrElse(
            usuario -> {
                usuario.desativar();
                usuarioRepository.save(usuario);
                log.info("Usuário da equipe {} desativado com sucesso.", event.usuarioId());
            },
            () -> log.warn("Usuário da equipe {} não encontrado para desativação via evento.", event.usuarioId())
        );
    }
}
