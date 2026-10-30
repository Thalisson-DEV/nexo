package com.nexo.app.modules.notificacao.application.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.nexo.app.modules.notificacao.domain.CategoriaNotificacao;
import com.nexo.app.modules.notificacao.domain.ModoGatilho;
import com.nexo.app.modules.notificacao.domain.OperadorCondicao;
import com.nexo.app.modules.notificacao.domain.PrioridadeNotificacao;
import com.nexo.app.modules.notificacao.domain.TipoDestinatarioRegra;

public record RegraNotificacaoResponse(
        UUID id,
        String chaveGatilho,
        String gatilhoNome,
        ModoGatilho modo,
        String nome,
        String descricao,
        /** Regra de fábrica: editável e desligável, nunca removível. */
        boolean sistema,
        boolean ativo,
        CategoriaNotificacao categoria,
        PrioridadeNotificacao prioridade,
        String tituloTemplate,
        String corpoTemplate,
        String deeplinkTemplate,
        Integer limiar,
        int cooldownMinutos,
        boolean enviarEmail,
        /** Se a categoria escolhida aceita o canal — a tela usa para desabilitar a opção. */
        boolean permiteEmail,
        List<CondicaoResponse> condicoes,
        List<DestinatarioResponse> destinatarios,
        OffsetDateTime dataCriacao
) {

    public record CondicaoResponse(
            UUID id,
            String campo,
            OperadorCondicao operador,
            String operadorRotulo,
            String valor
    ) {
    }

    public record DestinatarioResponse(
            UUID id,
            TipoDestinatarioRegra tipo,
            String tipoRotulo,
            UUID idAlvo,
            String valorAlvo
    ) {
    }
}
