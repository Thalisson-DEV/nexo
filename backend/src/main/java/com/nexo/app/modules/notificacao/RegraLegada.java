package com.nexo.app.modules.notificacao;

import java.util.List;
import java.util.UUID;

/** Uma regra de notificação do modelo antigo, com tudo o que a conversão precisa. */
public record RegraLegada(
        UUID id,
        String chaveGatilho,
        String nome,
        String descricao,
        boolean ativa,
        boolean sistema,
        String categoria,
        String prioridade,
        String titulo,
        String corpo,
        String deeplink,
        Integer limiar,
        int cooldownMinutos,
        boolean enviarEmail,
        List<Condicao> condicoes,
        List<Destinatario> destinatarios
) {

    public record Condicao(String campo, String operador, String valor) {
    }

    /** {@code politica} preenchida quando o tipo é POLITICA. */
    public record Destinatario(String tipo, UUID idAlvo, String valorAlvo, Politica politica) {
    }

    public record Politica(UUID id, String nome, List<Rota> rotas) {
    }

    public record Rota(int ordem, boolean padrao, UUID usuarioId, List<Condicao> condicoes) {
    }
}
