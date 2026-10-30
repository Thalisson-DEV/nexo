package com.nexo.app.modules.assistente.application.dto;

import com.nexo.app.modules.assistente.application.agent.ContextoCes;
import com.nexo.app.modules.assistente.application.agent.Packet;
import com.nexo.app.modules.assistente.domain.PapelMensagem;
import com.nexo.app.modules.assistente.domain.StatusMensagemAssistente;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Mensagem do historico. Respostas do assistente trazem os pacotes compactados da execucao para que a
 * interface reconstrua a linha do tempo (ferramentas, artefatos, arquivos, fontes) igual ao streaming.
 * Perguntas feitas em um painel trazem em {@code contexto} o snapshot da tela naquele momento.
 */
public record MensagemResponse(
        UUID id,
        UUID clientMessageId,
        PapelMensagem papel,
        StatusMensagemAssistente status,
        String conteudo,
        List<Packet> pacotes,
        List<AcaoInterface> acoes,
        List<ArquivoAssistenteResponse> arquivos,
        OffsetDateTime dataCriacao,
        Metricas metricas,
        ContextoCes contexto) {
    public record Metricas(String modelo, Integer tokensEntrada, Integer tokensSaida, Long duracaoMs, Integer ciclos,
            Integer chamadasFerramenta) {
    }
}
