package com.nexo.app.modules.notificacao.application.usecases;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexo.app.modules.notificacao.domain.ExecucaoRegra;
import com.nexo.app.modules.notificacao.infrastructure.persistence.RegraNotificacaoRepository;
import com.nexo.app.shared.domain.events.EventoNotificavel;

import lombok.RequiredArgsConstructor;

/**
 * O motor: dado um gatilho, descobre quais regras respondem por ele e manda cada
 * uma ser aplicada.
 *
 * <p>Os dois caminhos de entrada refletem as duas naturezas de gatilho. Um evento de
 * domínio chega por {@link #processarEvento} e é avaliado na hora. Uma situação ("há
 * N pendentes") não chega sozinha: é lida periodicamente pela varredura, que então
 * chama {@link #avaliarLimiar} — porque nenhum evento é emitido quando o décimo
 * desvio simplesmente continua aberto.</p>
 *
 * <p>Esta classe só orquestra e trabalha com identificadores. Quem aplica a regra é o
 * {@link AplicadorRegra}, em transação separada por regra.</p>
 */
@Service
@RequiredArgsConstructor
public class MotorNotificacoes {

    private final RegraNotificacaoRepository regraRepository;
    private final AplicadorRegra aplicadorRegra;

    /**
     * Avalia todas as regras ativas do gatilho do evento.
     *
     * @return quantas notificações foram gravadas ao todo
     */
    @Transactional(readOnly = true)
    public int processarEvento(EventoNotificavel evento) {
        List<UUID> idsRegra = regraRepository.buscarIdsAtivosPorGatilho(evento.chaveGatilho());
        if (idsRegra.isEmpty()) {
            return 0;
        }

        // O escopo do cooldown é o registro: silencia avisos repetidos sobre o mesmo
        // desconto sem silenciar os outros descontos.
        String escopo = evento.idReferencia() == null
                ? ExecucaoRegra.ESCOPO_GLOBAL : evento.idReferencia().toString();

        int entregues = 0;
        for (UUID idRegra : idsRegra) {
            entregues += aplicadorRegra.aplicar(idRegra, evento.contexto(), evento.papeis(),
                    evento.idReferencia(), escopo, null);
        }
        return entregues;
    }

    /**
     * Avalia uma regra por limiar contra o valor medido pelo módulo dono.
     *
     * <p>O escopo do cooldown é global porque a fila pendente é uma só: reavisar a
     * cada varredura enquanto ninguém tratou seria spam, e o silêncio configurado na
     * regra é exatamente a resposta a isso.</p>
     */
    public int avaliarLimiar(UUID idRegra, long valorAtual) {
        Map<String, Object> contexto = new HashMap<>();
        contexto.put("total", valorAtual);

        return aplicadorRegra.aplicar(idRegra, contexto, Map.of(), null,
                ExecucaoRegra.ESCOPO_GLOBAL, valorAtual);
    }
}
