package com.nexo.app.modules.notificacao.domain;

/**
 * Dimensão pela qual o público-alvo de um comunicado é descrito.
 *
 * <p>São as quatro que se resolvem nos dois sentidos com o que o IAM já expõe:
 * dizer se <em>este</em> usuário pertence ao público (para montar o mural) e
 * expandir o público em uma lista de usuários (para o sininho). Base operacional,
 * função e equipe são extensões naturais, mas dependem de novas consultas em RH —
 * ficaram de fora até que exista o caso de uso.</p>
 */
public enum TipoAlvoSegmento {

    /** Toda a empresa. */
    TODOS("Todos os usuários"),

    /** Um perfil de acesso inteiro (ex.: COORDENADOR_SESMT). */
    PERFIL("Perfil de acesso"),

    /** Um usuário nomeado. */
    USUARIO("Usuário específico"),

    /** ADMINISTRATIVO ou CAMPO. */
    TIPO_USUARIO("Tipo de usuário");

    private final String rotulo;

    TipoAlvoSegmento(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }

    public boolean exigeIdAlvo() {
        return this == PERFIL || this == USUARIO;
    }

    public boolean exigeValorAlvo() {
        return this == TIPO_USUARIO;
    }
}
