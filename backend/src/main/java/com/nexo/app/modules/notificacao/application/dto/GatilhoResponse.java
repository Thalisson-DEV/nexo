package com.nexo.app.modules.notificacao.application.dto;

import java.util.List;

import com.nexo.app.modules.notificacao.domain.CategoriaNotificacao;
import com.nexo.app.modules.notificacao.domain.ModoGatilho;

/**
 * Um gatilho do catálogo, com tudo que a tela de regras precisa para se montar
 * sozinha: quais variáveis o administrador pode usar nos textos e nas condições, e
 * quais papéis pode endereçar.
 */
public record GatilhoResponse(
        String chave,
        String nome,
        String descricao,
        ModoGatilho modo,
        String modoRotulo,
        CategoriaNotificacao categoriaPadrao,
        /** Utilizáveis como {@code {{variavel}}} e como campo de condição. */
        List<String> variaveis,
        /** Endereçáveis por destinatário do tipo PAPEL_DINAMICO. */
        List<String> papeis
) {
}
