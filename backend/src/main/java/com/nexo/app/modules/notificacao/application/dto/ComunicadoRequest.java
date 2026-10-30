package com.nexo.app.modules.notificacao.application.dto;

import java.util.List;

import com.nexo.app.modules.notificacao.domain.ApresentacaoComunicado;
import com.nexo.app.modules.notificacao.domain.PrioridadeNotificacao;
import com.nexo.app.modules.notificacao.domain.TipoComunicado;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Criação e edição do comunicado enquanto ele é rascunho.
 *
 * <p>Os segmentos vêm junto para que a tela de criação seja uma só: quem escreve o
 * comunicado decide o público no mesmo formulário. Numa edição, a lista informada
 * substitui a anterior por inteiro.</p>
 */
public record ComunicadoRequest(

        @NotBlank(message = "O título é obrigatório.")
        @Size(max = 150, message = "O título deve ter no máximo 150 caracteres.")
        String titulo,

        @Size(max = 300, message = "O resumo deve ter no máximo 300 caracteres.")
        String resumo,

        @NotBlank(message = "O conteúdo é obrigatório.")
        String conteudo,

        @NotNull(message = "O tipo do comunicado é obrigatório.")
        TipoComunicado tipo,

        @NotNull(message = "A forma de apresentação é obrigatória.")
        ApresentacaoComunicado apresentacao,

        @Size(max = 500, message = "O link do vídeo deve ter no máximo 500 caracteres.")
        String videoUrl,

        @Size(max = 50, message = "O rótulo da ação deve ter no máximo 50 caracteres.")
        String acaoRotulo,

        @Size(max = 500, message = "O destino da ação deve ter no máximo 500 caracteres.")
        String acaoDeeplink,

        boolean fixado,

        /** Exige "Li e entendi" e registra quem confirmou. Impede o adiamento. */
        boolean exigeConfirmacao,

        /** Também entrega uma notificação na caixa de entrada de cada destinatário. */
        boolean notificarNoSininho,

        /**
         * Também envia o informe por e-mail — para alcançar quem não vai abrir o
         * sistema a tempo. Respeita a preferência de cada destinatário. Ausente vira
         * ligado, que é o padrão do canal.
         */
        Boolean notificarPorEmail,

        PrioridadeNotificacao prioridadeNotificacao,

        @Valid
        List<SegmentoComunicadoRequest> segmentos
) {

    public ComunicadoRequest {
        notificarPorEmail = notificarPorEmail == null || notificarPorEmail;
    }
}
