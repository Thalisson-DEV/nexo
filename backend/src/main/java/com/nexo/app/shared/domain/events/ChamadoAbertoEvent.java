package com.nexo.app.shared.domain.events;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Um chamado acabou de entrar na fila.
 *
 * <p>Fecha o buraco mais visível do ciclo de vida do chamado: havia aviso de
 * atribuição, de mensagem, de resolução e de reabertura, mas nenhum para o instante em
 * que o problema chega. Sem ele, a equipe de suporte só descobre o chamado novo se
 * alguém pensar em abrir a fila — o que faz o SLA de primeira resposta correr contra
 * um relógio que ninguém está olhando.</p>
 *
 * <p>O papel {@code equipeAtendimento} chega com os membros já resolvidos porque só o
 * módulo de chamados sabe quem os compõe. É o mesmo motivo pelo qual
 * {@code ChamadoAtribuidoEvent} carrega o responsável: o motor de notificações não
 * conhece o domínio de ninguém.</p>
 *
 * <p>Equipe sem membros cadastrados devolve o papel vazio, e não é defeito — durante a
 * implantação as equipes aceitam atribuição livre. Uma regra endereçada por permissão
 * ({@code chamado:atender}) alcança essas pessoas de qualquer forma, e é por isso que
 * a regra de fábrica usa os dois caminhos.</p>
 */
public record ChamadoAbertoEvent(
        UUID idChamado,
        String protocolo,
        String assunto,
        String topico,
        String prioridade,
        String equipe,
        UUID idSolicitante,
        Set<UUID> membrosEquipe
) implements EventoNotificavel {

    public static final String CHAVE_GATILHO = "chamado.aberto";

    public ChamadoAbertoEvent {
        membrosEquipe = membrosEquipe == null
                ? Set.of() : Set.copyOf(new LinkedHashSet<>(membrosEquipe));
    }

    @Override
    public String chaveGatilho() {
        return CHAVE_GATILHO;
    }

    @Override
    public UUID idReferencia() {
        return idChamado;
    }

    @Override
    public Map<String, Object> contexto() {
        Map<String, Object> contexto = new HashMap<>();
        contexto.put("protocolo", protocolo);
        contexto.put("assunto", assunto);
        contexto.put("topico", topico);
        contexto.put("prioridade", prioridade);
        contexto.put("equipe", equipe);
        contexto.put("idChamado", idChamado);
        return contexto;
    }

    /**
     * Quem abriu não entra em {@code equipeAtendimento} nem que seja membro dela: o
     * atendente que registra o próprio chamado já sabe que ele existe, e receber o
     * aviso do que acabou de escrever é o tipo de ruído que faz as pessoas desligarem
     * o canal inteiro.
     */
    @Override
    public Map<String, Set<UUID>> papeis() {
        Set<UUID> equipeSemAutor = new LinkedHashSet<>(membrosEquipe);
        equipeSemAutor.remove(idSolicitante);

        Map<String, Set<UUID>> papeis = new HashMap<>();
        if (!equipeSemAutor.isEmpty()) {
            papeis.put("equipeAtendimento", equipeSemAutor);
        }
        if (idSolicitante != null) {
            papeis.put("solicitante", Set.of(idSolicitante));
        }
        return papeis;
    }
}
