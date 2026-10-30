package com.nexo.app.modules.assistente.application.agent;

import java.util.Map;

/**
 * De onde o usuario abriu o Copiloto (pagina, entidade e filtros), para contextualizar a conversa.
 * Quando a pergunta nasce em um painel (dashboard), {@code painel} traz o contexto estruturado da tela.
 * Todos os campos vem do frontend e sao tratados como dado nao confiavel: servem de pista, nunca de
 * autorizacao — as ferramentas validam permissao e existencia a cada consulta.
 */
public record ContextoCes(
        String pagina,
        String rota,
        String entidadeTipo,
        String entidadeId,
        String rotulo,
        Map<String, String> filtros,
        ContextoPainel painel) {

    public ContextoCes(String pagina, String rota, String entidadeTipo, String entidadeId, String rotulo,
            Map<String, String> filtros) {
        this(pagina, rota, entidadeTipo, entidadeId, rotulo, filtros, null);
    }

    public ContextoCes {
        pagina = limitar(pagina, 120);
        rota = limitar(rota, 300);
        entidadeTipo = limitar(entidadeTipo, 60);
        entidadeId = limitar(entidadeId, 80);
        rotulo = limitar(rotulo, 200);
        filtros = ContextoPainel.Texto.mapa(filtros, ContextoPainel.MAX_FILTROS);
        if (painel != null && painel.vazio()) painel = null;
    }

    public boolean vazio() {
        return (pagina == null || pagina.isBlank()) && (entidadeTipo == null || entidadeTipo.isBlank())
                && (rotulo == null || rotulo.isBlank()) && filtros.isEmpty() && painel == null;
    }

    /** Conversas iniciadas em um painel ficam associadas a ele (origem DASHBOARD + id do painel). */
    public boolean dePainel() {
        return painel != null && painel.id() != null;
    }

    private static String limitar(String value, int max) {
        if (value == null) return null;
        String cleaned = value.replaceAll("[\\p{Cntrl}]", " ").trim();
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
    }
}
