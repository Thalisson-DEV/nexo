package com.nexo.app.shared.application.notificacao;

import java.util.List;

/**
 * O que o e-mail mostra do registro além do texto da regra: a ficha, já na ordem e no
 * formato de quem lê, e as ações que quem recebe pode tomar a partir dali.
 *
 * <p>A ficha substitui a que o e-mail montaria sozinho a partir do contexto do evento.
 * Aquela serve a qualquer gatilho, mas mostra o que o evento carrega, com o nome da
 * chave como rótulo e o valor cru ("PAGAMENTO", "123.00"); esta mostra o registro como
 * ele está, com rótulos e moeda.</p>
 *
 * @param ficha  pares rótulo/valor, na ordem de leitura; vazia mantém a ficha genérica
 * @param acoes  botões de decisão; vazia quando não há o que decidir (ou já foi decidido)
 * @param notaAcoes texto curto sob os botões, explicando o que eles fazem; vazio usa o padrão
 */
public record DetalhesNotificacao(List<Campo> ficha, List<Acao> acoes, String notaAcoes) {

    public DetalhesNotificacao {
        ficha = ficha == null ? List.of() : List.copyOf(ficha);
        acoes = acoes == null ? List.of() : List.copyOf(acoes);
    }

    public DetalhesNotificacao(List<Campo> ficha, List<Acao> acoes) {
        this(ficha, acoes, null);
    }

    /** Um par da ficha. O valor já vem formatado. */
    public record Campo(String rotulo, String valor) {
    }

    /**
     * Um botão de ação.
     *
     * @param caminho rota do frontend (ex.: {@code /financeiro/compras/{id}?acao=aprovar});
     *                o e-mail a torna absoluta. Abre o registro com a ação já pronta para
     *                confirmar — a decisão continua exigindo sessão e permissão.
     */
    public record Acao(String rotulo, String caminho, Estilo estilo) {
    }

    /** Intenção do botão, traduzida em cor pelo layout. */
    public enum Estilo {
        POSITIVA,
        NEGATIVA,
        NEUTRA
    }
}
