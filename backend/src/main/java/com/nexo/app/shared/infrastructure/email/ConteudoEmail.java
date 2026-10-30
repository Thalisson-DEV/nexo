package com.nexo.app.shared.infrastructure.email;

import java.util.List;

import lombok.Builder;

/**
 * O conteúdo de um e-mail nos termos do layout, não do HTML.
 *
 * <p>Este record <b>é</b> a promessa de que todo e-mail do Nexo tem a mesma cara: o
 * montador não aceita marcação livre, aceita estes campos. Um aviso de desconto e um
 * comunicado do mural diferem no que preenche cada campo e em nada mais — não há como
 * um deles ganhar um bloco que o outro não tenha, porque não há onde escrevê-lo.</p>
 *
 * <p>Quando um campo não se aplica, fica vazio e o bloco correspondente simplesmente
 * não é desenhado. É assim que a mesma estrutura serve a um aviso de uma linha e a um
 * comunicado com seis anexos.</p>
 *
 * @param etiqueta   a área a que o aviso pertence (ex.: "Aprovações")
 * @param prioridade a urgência, escrita por extenso ao lado da área. Fica vazia quando
 *                   o aviso é de rotina: anunciar "prioridade normal" em todo e-mail
 *                   gastaria a palavra e tiraria peso justamente de quando ela importa
 * @param destaque   tom da linha de identificação, que traduz urgência em cor
 * @param titulo     a manchete — costuma ser o mesmo título da notificação no sininho
 * @param paragrafos o texto, um parágrafo por item
 * @param dados      pares rótulo/valor da ficha (ex.: "Protocolo" → "CH-2026-0042")
 * @param rotuloAcao texto do botão; sem ele e sem {@code urlAcao} o botão não aparece
 * @param urlAcao    link rápido absoluto para o registro dentro do sistema
 * @param acoes      botões de decisão (aprovar, rejeitar...), desenhados acima do botão
 *                   principal; cada um abre o registro com a ação pronta para confirmar
 * @param notaAcoes  o que os botões fazem, sob eles; vazio usa o texto padrão
 * @param anexos     o que veio junto, listado no corpo para que se saiba o que esperar
 * @param htmlLivre  corpo rico já sanitizado por quem monta (automações: tabelas, análises).
 *                   Entra depois dos parágrafos, dentro do mesmo cartão institucional
 */
@Builder
public record ConteudoEmail(
        String etiqueta,
        String prioridade,
        DestaqueEmail destaque,
        String titulo,
        List<String> paragrafos,
        List<Dado> dados,
        String rotuloAcao,
        String urlAcao,
        List<Acao> acoes,
        String notaAcoes,
        List<Anexo> anexos,
        String htmlLivre
) {

    public ConteudoEmail {
        paragrafos = paragrafos == null ? List.of() : List.copyOf(paragrafos);
        dados = dados == null ? List.of() : List.copyOf(dados);
        anexos = anexos == null ? List.of() : List.copyOf(anexos);
        acoes = acoes == null ? List.of() : List.copyOf(acoes);
        destaque = destaque == null ? DestaqueEmail.NEUTRO : destaque;
    }

    /** Um par da ficha. O valor já vem formatado para leitura humana. */
    public record Dado(String rotulo, String valor) {
    }

    /**
     * Um botão de decisão. O tom vira cor: verde para aceitar, vermelho para recusar —
     * mas o rótulo diz a ação por extenso, porque o app do Gmail inverte cores.
     */
    public record Acao(String rotulo, String url, Tom tom) {
    }

    public enum Tom {
        POSITIVA,
        NEGATIVA,
        NEUTRA
    }

    /**
     * Um arquivo citado no corpo.
     *
     * @param anexado {@code false} quando o arquivo existe mas não coube no e-mail —
     *                aparece na lista mesmo assim, para que quem recebe saiba que ele
     *                está lá e vá buscá-lo pelo link em vez de supor que não há nada.
     */
    public record Anexo(String nomeArquivo, long tamanhoBytes, boolean anexado) {
    }
}
