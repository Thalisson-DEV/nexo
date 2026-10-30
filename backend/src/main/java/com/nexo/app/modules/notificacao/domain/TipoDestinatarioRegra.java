package com.nexo.app.modules.notificacao.domain;

/**
 * Como uma regra descreve quem deve ser notificado.
 */
public enum TipoDestinatarioRegra {

    /**
     * Todos que possuem uma permissão (ex.: {@code desconto:aprovar}).
     *
     * <p>É a forma mais durável: quando alguém ganha ou perde a atribuição, a lista
     * de notificados acompanha sozinha, sem ninguém lembrar de editar a regra.</p>
     */
    PERMISSAO("Quem tem a permissão"),

    PERFIL("Todos de um perfil de acesso"),

    USUARIO("Usuário específico"),

    /** Política cadastrada que escolhe exatamente um usuário pelo contexto. */
    POLITICA("Política de roteamento"),

    /**
     * Um papel que só existe dentro do evento — "o solicitante", "o responsável".
     * Disponível apenas em gatilhos {@link ModoGatilho#POR_OCORRENCIA}, porque é o
     * evento que carrega quem ocupa o papel.
     */
    PAPEL_DINAMICO("Papel no próprio registro");

    private final String rotulo;

    TipoDestinatarioRegra(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }

    public boolean exigeIdAlvo() {
        return this == PERFIL || this == USUARIO || this == POLITICA;
    }

    public boolean exigeValorAlvo() {
        return this == PERMISSAO || this == PAPEL_DINAMICO;
    }
}
