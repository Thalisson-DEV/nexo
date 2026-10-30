package com.nexo.app.shared.infrastructure.email;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

import lombok.Builder;

/**
 * A mensagem como o adaptador de envio a recebe: destinatários, assunto e corpo já
 * prontos.
 *
 * <p>Nada aqui sabe de notificação, regra ou usuário — é o contrato mínimo que tanto
 * SMTP quanto Resend conseguem cumprir. Quem traduz uma notificação nisto é o módulo
 * de notificações; quem quiser mandar um e-mail que não venha de notificação nenhuma
 * usa a mesma porta.</p>
 *
 * <p>O {@link #texto()} não é decoração: cliente que recusa HTML e filtro de spam que
 * pontua ausência de alternativa em texto são motivos reais para toda mensagem sair
 * nas duas formas.</p>
 */
@Builder
public record MensagemEmail(
        Set<String> destinatarios,
        String assunto,
        String html,
        String texto,
        List<AnexoEmail> anexos
) {

    public MensagemEmail {
        if (destinatarios == null || destinatarios.isEmpty()) {
            throw new InvarianteVioladaException("Informe ao menos um destinatário de e-mail.");
        }
        if (assunto == null || assunto.isBlank()) {
            throw new InvarianteVioladaException("O e-mail precisa de um assunto.");
        }
        if (html == null || html.isBlank()) {
            throw new InvarianteVioladaException("O e-mail precisa de um corpo.");
        }
        destinatarios = Set.copyOf(new LinkedHashSet<>(destinatarios));
        anexos = anexos == null ? List.of() : List.copyOf(anexos);
    }

    public boolean temAnexos() {
        return !anexos.isEmpty();
    }
}
