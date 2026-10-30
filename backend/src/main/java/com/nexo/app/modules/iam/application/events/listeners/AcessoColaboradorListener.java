package com.nexo.app.modules.iam.application.events.listeners;

import com.nexo.app.modules.iam.application.dto.UsuarioRequest;
import com.nexo.app.modules.iam.application.usecases.UsuarioUseCases;
import com.nexo.app.shared.domain.events.AcessoColaboradorSolicitadoEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Escuta eventos do módulo RH para processar a criação de usuários
 * de forma desacoplada no módulo IAM.
 *
 * <p>O aviso de volta para o RH (vínculo inverso do colaborador) é responsabilidade do
 * próprio {@link UsuarioUseCases#criar}, para valer também quando o usuário é criado
 * direto pelo CRUD do IAM.</p>
 */
@Component
@RequiredArgsConstructor
public class AcessoColaboradorListener {

    private final UsuarioUseCases usuarioUseCases;

    @ApplicationModuleListener
    public void on(AcessoColaboradorSolicitadoEvent event) {
        UsuarioRequest request = new UsuarioRequest(
            event.login(),
            event.email(),
            event.telefone(),
            event.senha(),
            event.idPerfil(),
            event.colaboradorId(),
            event.tipo()
        );

        usuarioUseCases.criar(request);
    }
}
