package com.nexo.app.modules.iam.application.events.listeners;

import com.nexo.app.modules.iam.infrastructure.persistence.UsuarioRepository;
import com.nexo.app.shared.domain.events.ColaboradorDesativadoEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Escuta eventos de desativação de colaboradores vindos do módulo RH
 * para desativar o acesso (Usuario) correspondente no IAM.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ColaboradorDesativadoListener {

    private final UsuarioRepository usuarioRepository;

    @ApplicationModuleListener
    public void on(ColaboradorDesativadoEvent event) {
        usuarioRepository.findById(event.usuarioId()).ifPresentOrElse(
            usuario -> {
                usuario.desativar();
                usuarioRepository.save(usuario);
            },
            () -> log.warn("Usuário {} não encontrado para desativação via evento.", event.usuarioId())
        );
    }
}
