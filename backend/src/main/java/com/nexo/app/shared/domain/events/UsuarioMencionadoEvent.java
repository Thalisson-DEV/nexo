package com.nexo.app.shared.domain.events;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Uma ou mais pessoas foram marcadas em um texto.
 *
 * <p>Um evento para o conjunto, e não um por marcado: quem escreveu produziu um fato
 * só ("nesta mensagem, marquei fulano e beltrano"), e é esse fato que as regras
 * avaliam. O fan-out por destinatário acontece adiante, no publicador.</p>
 *
 * <p>O {@code link} é variável de contexto em vez de caminho fixo na regra porque a
 * marcação nasce de origens diferentes — hoje o chamado, amanhã um desvio ou uma
 * solicitação — e só quem registrou sabe para onde ela leva. O template da regra de
 * fábrica usa {@code {{link}}} como destino, o que a mantém correta para toda origem
 * futura sem precisar ser reescrita.</p>
 *
 * <p>O {@link #idReferencia()} é o id do <b>texto</b> onde as marcações foram escritas
 * (a interação do chamado), não o do registro comentado. Um chamado recebe muitas
 * mensagens e cada uma pode marcar alguém; como a chave de idempotência do motor
 * deriva daqui, apontar para o chamado faria a segunda marcação ser descartada como
 * repetição da primeira.</p>
 */
public record UsuarioMencionadoEvent(
        UUID idOrigem,
        UUID idRegistro,
        String tipoOrigem,
        String origem,
        String autor,
        String trecho,
        String link,
        Set<UUID> mencionados
) implements EventoNotificavel {

    public static final String CHAVE_GATILHO = "mencao.usuario_marcado";

    @Override
    public String chaveGatilho() {
        return CHAVE_GATILHO;
    }

    @Override
    public UUID idReferencia() {
        return idOrigem;
    }

    @Override
    public Map<String, Object> contexto() {
        Map<String, Object> contexto = new HashMap<>();
        contexto.put("autor", autor);
        contexto.put("trecho", trecho);
        contexto.put("origem", origem);
        contexto.put("tipoOrigem", tipoOrigem);
        contexto.put("link", link);
        return contexto;
    }

    @Override
    public Map<String, Set<UUID>> papeis() {
        return mencionados == null || mencionados.isEmpty()
                ? Map.of()
                : Map.of("mencionado", Set.copyOf(mencionados));
    }
}
