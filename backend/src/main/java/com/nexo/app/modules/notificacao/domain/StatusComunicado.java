package com.nexo.app.modules.notificacao.domain;

/**
 * Ciclo de vida editorial do comunicado.
 *
 * <p>Não existe estado "agendado": um comunicado publicado com início no futuro já
 * é {@link #PUBLICADO} e simplesmente ainda não entrou na janela de exibição. Isso
 * evita um job cuja única função seria virar a chave na hora marcada — e, com ele,
 * a chance de o comunicado não aparecer porque o job falhou.</p>
 */
public enum StatusComunicado {

    RASCUNHO("Rascunho"),
    PUBLICADO("Publicado"),
    ARQUIVADO("Arquivado");

    private final String rotulo;

    StatusComunicado(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
