package com.nexo.app.shared.domain.events;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Alguém escreveu uma mensagem pública em um chamado.
 *
 * <p>Só mensagens públicas chegam aqui. Comentário interno é conversa da equipe sobre
 * o chamado, não com o solicitante, e transformá-lo em notificação vazaria para fora
 * um texto que a tela esconde de propósito.</p>
 *
 * <p>O evento resolve os <b>dois</b> lados da conversa e omite quem escreveu, de modo
 * que uma única regra endereçando {@code solicitante} e {@code responsavel} sempre
 * avisa o outro lado, seja quem for o autor. Sem essa omissão, cada resposta
 * notificaria também quem acabou de digitá-la.</p>
 *
 * <p>Como em {@link ChamadoAtribuidoEvent}, o {@link #idReferencia()} é a interação:
 * um chamado tem muitas mensagens, e a idempotência do motor engoliria da segunda em
 * diante se a referência fosse o chamado.</p>
 */
public record ChamadoMensagemRecebidaEvent(
        UUID idInteracao,
        UUID idChamado,
        String protocolo,
        String assunto,
        String autor,
        String trecho,
        UUID idAutor,
        UUID idSolicitante,
        UUID idResponsavel
) implements EventoNotificavel {

    public static final String CHAVE_GATILHO = "chamado.mensagem_recebida";

    @Override
    public String chaveGatilho() {
        return CHAVE_GATILHO;
    }

    @Override
    public UUID idReferencia() {
        return idInteracao;
    }

    @Override
    public Map<String, Object> contexto() {
        Map<String, Object> contexto = new HashMap<>();
        contexto.put("protocolo", protocolo);
        contexto.put("assunto", assunto);
        contexto.put("autor", autor);
        contexto.put("trecho", trecho);
        contexto.put("idChamado", idChamado);
        return contexto;
    }

    @Override
    public Map<String, Set<UUID>> papeis() {
        Map<String, Set<UUID>> papeis = new LinkedHashMap<>();
        if (idSolicitante != null && !idSolicitante.equals(idAutor)) {
            papeis.put("solicitante", Set.of(idSolicitante));
        }
        if (idResponsavel != null && !idResponsavel.equals(idAutor)) {
            papeis.put("responsavel", Set.of(idResponsavel));
        }
        return papeis;
    }
}
