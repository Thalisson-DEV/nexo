package com.nexo.app.modules.mencao.domain;

/**
 * De onde a marcação veio.
 *
 * <p>Catálogo fechado pelo mesmo motivo do catálogo de gatilhos: cada origem exige um
 * módulo que saiba montar o contexto e o link de volta. Acrescentar uma é uma
 * constante aqui mais a chamada no módulo de origem — não há como "cadastrar" uma
 * origem que ninguém alimenta.</p>
 *
 * <p>A {@link #getPermissaoVisibilidadeRestrita()} é o que impede o furo óbvio da
 * marcação: em um texto de circulação restrita (o comentário interno de um chamado),
 * marcar quem não pode lê-lo geraria um aviso com um trecho que a pessoa não consegue
 * abrir. Cada origem declara qual permissão delimita esse círculo.</p>
 */
public enum OrigemMencao {

    CHAMADO_INTERACAO("Chamado", "chamado:manage");

    private final String rotulo;
    private final String permissaoVisibilidadeRestrita;

    OrigemMencao(String rotulo, String permissaoVisibilidadeRestrita) {
        this.rotulo = rotulo;
        this.permissaoVisibilidadeRestrita = permissaoVisibilidadeRestrita;
    }

    public String getRotulo() {
        return rotulo;
    }

    /** Permissão exigida de quem é marcado quando o texto tem circulação restrita. */
    public String getPermissaoVisibilidadeRestrita() {
        return permissaoVisibilidadeRestrita;
    }
}
