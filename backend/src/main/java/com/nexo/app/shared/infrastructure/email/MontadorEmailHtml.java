package com.nexo.app.shared.infrastructure.email;

import java.util.Locale;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;

/**
 * Transforma um {@link ConteudoEmail} no HTML final. É o único lugar do sistema que
 * escreve marcação de e-mail.
 *
 * <p>O HTML é deliberadamente antiquado — tabelas aninhadas, estilo inline, largura
 * fixa de 640px. Não é desleixo: o Outlook renderiza e-mail com o motor do Word, que
 * ignora {@code <div>} com flexbox e boa parte de CSS em {@code <style>}. Layout que
 * parece moderno no navegador chega quebrado na caixa de quem precisa aprovar o
 * desconto.</p>
 *
 * <p><b>O registro é institucional, e cada escolha aqui defende isso.</b> Cantos retos
 * em vez de arredondados, filete em vez de pílula colorida, identificação em texto em
 * vez de etiqueta com fundo, nenhum emoji, e um rodapé que informa em vez de justificar
 * o envio. São exatamente os traços que separam um comunicado de trabalho de uma peça
 * de marketing — e a diferença decide se quem recebe trata o aviso como obrigação ou
 * como ruído.</p>
 */
@Component
@RequiredArgsConstructor
public class MontadorEmailHtml {

    /**
     * A paleta é a do frontend Angular, valor por valor: azul da marca do
     * {@code logo.svg} e a escala slate do Tailwind que o {@code styles.css} usa
     * nas superfícies e nos textos. O e-mail e o sistema precisam ser lidos como o
     * mesmo produto — cor aproximada seria pior que cor nenhuma.
     */
    private static final String AZUL_MARCA = "#1E3A8A";
    private static final String AZUL_LINK = "#2563eb";
    private static final String TEXTO_FORTE = "#0f172a";
    private static final String TEXTO_CORPO = "#334155";
    private static final String TEXTO_SUAVE = "#64748b";
    private static final String BORDA = "#e2e8f0";
    private static final String FUNDO_PAGINA = "#f1f5f9";
    private static final String FUNDO_FICHA = "#f8fafc";
    private static final String VERDE_ACAO = "#15803d";
    private static final String VERMELHO_ACAO = "#b91c1c";

    /** Servido pelo próprio frontend, ao lado do {@code index.html}. */
    private static final String CAMINHO_LOGO = "/assets/logo-email.png";

    /** A mesma logo com as formas em branco, para quando o cliente escurece o e-mail à força. */
    private static final String CAMINHO_LOGO_ESCURO = "/assets/logo-email-escuro.png";

    private static final String FONTE =
            "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";

    private final EmailProperties propriedades;

    public String montar(ConteudoEmail conteudo) {
        StringBuilder html = new StringBuilder(6144);

        html.append("<!DOCTYPE html><html lang=\"pt-BR\"><head>")
                .append("<meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">");
        travaDeTemaClaro(html);
        html.append("</head><body class=\"ces-pagina\" style=\"margin:0;padding:0;")
                .append("background-color:").append(FUNDO_PAGINA).append(";\">");

        // Pré-cabeçalho: some na renderização mas é o texto que a caixa de entrada
        // mostra ao lado do assunto na listagem.
        html.append("<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;\">")
                .append(escapar(previa(conteudo))).append("</div>");

        html.append("<div class=\"ces-pagina\" style=\"background-color:").append(FUNDO_PAGINA)
                .append(";margin:0;padding:24px 12px;font-family:").append(FONTE).append(";\">");

        html.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" ")
                .append("width=\"100%\" class=\"ces-cartao\" style=\"max-width:640px;margin:0 auto;")
                .append("background-color:#ffffff;border:1px solid ").append(BORDA)
                .append(";border-radius:8px;overflow:hidden;\">");

        cabecalho(html);
        corpo(html, conteudo);
        rodape(html);

        html.append("</table></div></body></html>");
        return html.toString();
    }

    /**
     * Impede que o cliente de e-mail reescreva as cores no tema escuro.
     *
     * <p>Sem isto, o aviso chega invertido: fundo preto, texto claro e o azul da marca
     * deslocado. Não é só questão de gosto — o cabeçalho branco com o logo vira um
     * retângulo claro isolado no meio do escuro, e o e-mail deixa de parecer o mesmo
     * produto que a aplicação.</p>
     *
     * <p>São três defesas, porque cada família de cliente responde a uma:</p>
     * <ul>
     *   <li>{@code color-scheme}/{@code supported-color-schemes} — o caminho oficial,
     *       respeitado por Apple Mail, iOS e Outlook moderno. O {@code only} é o que
     *       diz "não adapte", em vez de apenas "prefiro claro".</li>
     *   <li>{@code [data-ogsc]}/{@code [data-ogsb]} — o Outlook.com reescreve as cores
     *       e <i>marca</i> os elementos que tocou com esses atributos. Reaplicar a cor
     *       por eles é a única forma de desfazer o que ele já fez.</li>
     *   <li>Estilo inline em cada elemento, que o montador inteiro já usa — é o que
     *       sobra quando o cliente descarta o bloco {@code <style>}.</li>
     * </ul>
     *
     * <p><b>O app do Gmail no celular continua fora de alcance:</b> ele inverte por
     * heurística própria, ignora {@code color-scheme} e não marca o que alterou. Para
     * esse caso não existe defesa, só mitigação — e é por isso que nenhum texto do
     * layout depende de cor para ser compreendido.</p>
     */
    private static void travaDeTemaClaro(StringBuilder html) {
        html.append("<meta name=\"color-scheme\" content=\"light\">")
                .append("<meta name=\"supported-color-schemes\" content=\"light\">")
                .append("<style>")
                .append(":root{color-scheme:light only;supported-color-schemes:light only;}")
                .append("[data-ogsc] .ces-pagina,[data-ogsb] .ces-pagina")
                .append("{background-color:").append(FUNDO_PAGINA).append(" !important;}")
                .append("[data-ogsc] .ces-cartao,[data-ogsb] .ces-cartao,")
                .append("[data-ogsc] .ces-cabecalho,[data-ogsb] .ces-cabecalho")
                .append("{background-color:#ffffff !important;}")
                .append("[data-ogsc] .ces-superficie,[data-ogsb] .ces-superficie")
                .append("{background-color:").append(FUNDO_FICHA).append(" !important;}")
                .append("[data-ogsc] .ces-botao,[data-ogsb] .ces-botao")
                .append("{background-color:").append(AZUL_MARCA).append(" !important;}")
                .append("[data-ogsc] .ces-botao a,[data-ogsb] .ces-botao a")
                .append("{color:#ffffff !important;}")
                .append("[data-ogsc] .ces-forte,[data-ogsb] .ces-forte")
                .append("{color:").append(TEXTO_FORTE).append(" !important;}")
                .append("[data-ogsc] .ces-corpo,[data-ogsb] .ces-corpo")
                .append("{color:").append(TEXTO_CORPO).append(" !important;}")
                .append("[data-ogsc] .ces-suave,[data-ogsb] .ces-suave")
                .append("{color:").append(TEXTO_SUAVE).append(" !important;}")
                .append("[data-ogsc] .ces-marca,[data-ogsb] .ces-marca")
                .append("{color:").append(AZUL_MARCA).append(" !important;}")
                // Outlook.com escureceu mesmo assim: troca para a logo de formas brancas.
                .append("[data-ogsc] .ces-logo-claro{display:none !important;}")
                .append("[data-ogsc] .ces-logo-escuro{display:block !important;max-height:none !important;}")
                .append("</style>");
    }

    /**
     * A versão em texto puro da mesma mensagem. Cliente que recusa HTML mostra isto, e
     * filtro de spam pontua contra quem não oferece alternativa.
     */
    public String montarTexto(ConteudoEmail conteudo) {
        StringBuilder texto = new StringBuilder(1024);
        texto.append("NEXO — GESTÃO OPERACIONAL\n");
        texto.append("========================================\n\n");

        String identificacao = identificacao(conteudo);
        if (StringUtils.hasText(identificacao)) {
            texto.append(identificacao.toUpperCase(Locale.ROOT)).append("\n\n");
        }
        texto.append(conteudo.titulo()).append("\n\n");

        for (String paragrafo : conteudo.paragrafos()) {
            texto.append(paragrafo).append("\n\n");
        }
        if (StringUtils.hasText(conteudo.htmlLivre())) {
            texto.append(org.jsoup.Jsoup.parse(conteudo.htmlLivre()).wholeText().strip()).append("\n\n");
        }
        for (ConteudoEmail.Dado dado : conteudo.dados()) {
            texto.append(dado.rotulo()).append(": ").append(dado.valor()).append('\n');
        }
        if (!conteudo.dados().isEmpty()) {
            texto.append('\n');
        }
        for (ConteudoEmail.Acao acao : conteudo.acoes()) {
            texto.append(acao.rotulo()).append(":\n").append(acao.url()).append("\n\n");
        }
        if (StringUtils.hasText(conteudo.urlAcao())) {
            texto.append(rotuloAcao(conteudo)).append(":\n")
                    .append(conteudo.urlAcao()).append("\n\n");
        }
        if (!conteudo.anexos().isEmpty()) {
            texto.append("Anexos\n");
            for (ConteudoEmail.Anexo anexo : conteudo.anexos()) {
                texto.append("  ").append(anexo.nomeArquivo())
                        .append(" (").append(formatarTamanho(anexo.tamanhoBytes())).append(')')
                        .append(anexo.anexado() ? "" : " — disponível no sistema")
                        .append('\n');
            }
            texto.append('\n');
        }
        texto.append("----------------------------------------\n")
                .append("Mensagem automática do sistema Nexo. Não responda a este e-mail.\n");
        return texto.toString();
    }

    /**
     * Cabeçalho branco com o logo à esquerda e o nome do sistema à direita, sobre um
     * filete da cor da marca — o mesmo enquadramento do topo da aplicação.
     *
     * <p>O logo é sempre acompanhado do nome em texto, e não substituído por ele: um
     * cliente de e-mail que bloqueia imagem remota (o padrão do Outlook corporativo)
     * deixaria o cabeçalho vazio, e um e-mail sem remetente identificável é o que mais
     * parece golpe. Com o texto ao lado, a imagem é ganho e não dependência.</p>
     */
    private void cabecalho(StringBuilder html) {
        html.append("<tr><td class=\"ces-cabecalho\" style=\"background-color:#ffffff;")
                .append("padding:20px 32px 16px;border-bottom:2px solid ")
                .append(AZUL_MARCA).append(";\">");

        html.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" ")
                .append("width=\"100%\"><tr>");

        String logo = urlLogo();
        if (logo != null) {
            // PNG transparente com um contorno branco fino: some no cartão branco e mantém a
            // logo legível quando o cliente escurece o e-mail sem avisar (app do Gmail), em
            // vez de deixar um retângulo branco no meio do escuro.
            html.append("<td style=\"vertical-align:middle;width:58px;\">")
                    .append("<img class=\"ces-logo-claro\" src=\"").append(escapar(logo))
                    .append("\" alt=\"Nexo\" width=\"46\" height=\"36\" ")
                    .append("style=\"display:block;border:0;width:46px;height:36px;\">");
            String escuro = urlLogoEscuro();
            if (escuro != null) {
                html.append("<img class=\"ces-logo-escuro\" src=\"").append(escapar(escuro))
                        .append("\" alt=\"Nexo\" width=\"46\" height=\"36\" ")
                        .append("style=\"display:none;max-height:0;overflow:hidden;mso-hide:all;border:0;width:46px;height:36px;\">");
            }
            html.append("</td>");
        }

        html.append("<td style=\"vertical-align:middle;\">")
                .append("<div class=\"ces-marca\" style=\"font-size:16px;font-weight:700;")
                .append("letter-spacing:0.5px;color:").append(AZUL_MARCA)
                .append(";line-height:1.2;\">Nexo</div>")
                .append("<div class=\"ces-suave\" style=\"font-size:12px;color:").append(TEXTO_SUAVE)
                .append(";line-height:1.4;\">Gestão Operacional</div>")
                .append("</td>");

        html.append("</tr></table></td></tr>");
    }

    /**
     * O logo vem do frontend por padrão, derivado da mesma raiz que já monta os links
     * dos botões. Assim o canal nasce com a marca sem exigir uma variável a mais — e
     * {@code EMAIL_LOGO_URL} continua valendo quando o arquivo mora em outro lugar.
     */
    /** Só a logo padrão tem versão escura; uma logo configurada à parte segue sozinha. */
    private String urlLogoEscuro() {
        if (StringUtils.hasText(propriedades.getLogoUrl())) return null;
        return propriedades.resolverUrl(CAMINHO_LOGO_ESCURO);
    }

    private String urlLogo() {
        if (StringUtils.hasText(propriedades.getLogoUrl())) {
            return propriedades.getLogoUrl().trim();
        }
        return propriedades.resolverUrl(CAMINHO_LOGO);
    }

    private void corpo(StringBuilder html, ConteudoEmail conteudo) {
        html.append("<tr><td style=\"padding:30px 32px 32px;\">");

        identificacao(html, conteudo);

        html.append("<h1 class=\"ces-forte\" style=\"margin:0 0 16px;font-size:19px;")
                .append("line-height:1.4;font-weight:600;color:").append(TEXTO_FORTE).append(";\">")
                .append(escapar(conteudo.titulo())).append("</h1>");

        for (String paragrafo : conteudo.paragrafos()) {
            html.append("<p class=\"ces-corpo\" style=\"margin:0 0 12px;font-size:14px;")
                    .append("line-height:1.65;color:").append(TEXTO_CORPO).append(";\">")
                    .append(escapar(paragrafo)).append("</p>");
        }

        if (StringUtils.hasText(conteudo.htmlLivre())) {
            html.append("<div class=\"ces-corpo\" style=\"font-size:14px;line-height:1.6;color:")
                    .append(TEXTO_CORPO).append(";\">").append(conteudo.htmlLivre()).append("</div>");
        }

        ficha(html, conteudo);
        acoes(html, conteudo);
        botao(html, conteudo);
        anexos(html, conteudo);

        html.append("</td></tr>");
    }

    /**
     * A linha "Aprovações · Prioridade alta" e o filete sob ela. Substitui a etiqueta em
     * pílula: diz a mesma coisa em palavras, que é como um sistema de gestão fala.
     */
    private void identificacao(StringBuilder html, ConteudoEmail conteudo) {
        if (!StringUtils.hasText(conteudo.etiqueta())
                && !StringUtils.hasText(conteudo.prioridade())) {
            return;
        }
        DestaqueEmail destaque = conteudo.destaque();

        html.append("<p class=\"ces-suave\" style=\"margin:0 0 10px;font-size:11px;")
                .append("font-weight:700;text-transform:uppercase;letter-spacing:1px;color:")
                .append(TEXTO_SUAVE).append(";\">");

        if (StringUtils.hasText(conteudo.etiqueta())) {
            html.append(escapar(conteudo.etiqueta()));
        }
        if (StringUtils.hasText(conteudo.prioridade())) {
            if (StringUtils.hasText(conteudo.etiqueta())) {
                html.append("<span style=\"color:").append(BORDA).append(";\"> &#183; </span>");
            }
            html.append("<span style=\"color:").append(destaque.getTexto()).append(";\">")
                    .append(escapar(conteudo.prioridade())).append("</span>");
        }
        html.append("</p>");

        html.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" ")
                .append("width=\"100%\" style=\"margin:0 0 20px;\"><tr>")
                .append("<td style=\"height:2px;line-height:2px;font-size:0;width:48px;")
                .append("background-color:").append(destaque.getRegua()).append(";\">&nbsp;</td>")
                .append("<td style=\"height:2px;line-height:2px;font-size:0;")
                .append("background-color:").append(BORDA).append(";\">&nbsp;</td>")
                .append("</tr></table>");
    }

    private void ficha(StringBuilder html, ConteudoEmail conteudo) {
        if (conteudo.dados().isEmpty()) {
            return;
        }
        html.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" ")
                .append("width=\"100%\" class=\"ces-superficie\" style=\"margin:20px 0;")
                .append("background-color:").append(FUNDO_FICHA)
                .append(";border:1px solid ").append(BORDA).append(";border-radius:6px;\">");

        boolean primeira = true;
        for (ConteudoEmail.Dado dado : conteudo.dados()) {
            String separador = primeira ? "" : "border-top:1px solid " + BORDA + ";";
            html.append("<tr>")
                    .append("<td class=\"ces-suave\" style=\"padding:10px 16px;font-size:12px;")
                    .append("color:").append(TEXTO_SUAVE)
                    .append(";white-space:nowrap;vertical-align:top;width:38%;").append(separador)
                    .append("\">").append(escapar(dado.rotulo())).append("</td>")
                    .append("<td class=\"ces-forte\" style=\"padding:10px 16px;font-size:13px;")
                    .append("font-weight:600;color:")
                    .append(TEXTO_FORTE).append(";vertical-align:top;").append(separador)
                    .append("\">").append(escapar(dado.valor())).append("</td>")
                    .append("</tr>");
            primeira = false;
        }
        html.append("</table>");
    }

    /**
     * Os botões de decisão, lado a lado. Cada um abre o registro com a ação já
     * preparada; a confirmação continua no sistema, com sessão e permissão — por isso
     * encaminhar o e-mail não dá a ninguém o poder de decidir.
     */
    private void acoes(StringBuilder html, ConteudoEmail conteudo) {
        if (conteudo.acoes().isEmpty()) {
            return;
        }
        html.append("<p class=\"ces-suave\" style=\"margin:24px 0 8px;font-size:12px;font-weight:700;")
                .append("text-transform:uppercase;letter-spacing:0.6px;color:").append(TEXTO_SUAVE)
                .append(";\">Decida por aqui</p>")
                .append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>");
        boolean primeira = true;
        for (ConteudoEmail.Acao acao : conteudo.acoes()) {
            String cor = switch (acao.tom() == null ? ConteudoEmail.Tom.NEUTRA : acao.tom()) {
                case POSITIVA -> VERDE_ACAO;
                case NEGATIVA -> VERMELHO_ACAO;
                case NEUTRA -> AZUL_MARCA;
            };
            if (!primeira) {
                html.append("<td style=\"width:10px;\">&nbsp;</td>");
            }
            html.append("<td style=\"background-color:").append(cor).append(";border-radius:6px;\">")
                    .append("<a href=\"").append(escapar(acao.url())).append("\" style=\"display:inline-block;")
                    .append("padding:12px 26px;font-size:14px;font-weight:700;color:#ffffff;")
                    .append("text-decoration:none;border-radius:6px;\">")
                    .append(escapar(acao.rotulo())).append("</a></td>");
            primeira = false;
        }
        html.append("</tr></table>")
                .append("<p class=\"ces-suave\" style=\"margin:8px 0 0;font-size:11px;line-height:1.5;color:")
                .append(TEXTO_SUAVE).append(";\">")
                .append(escapar(StringUtils.hasText(conteudo.notaAcoes()) ? conteudo.notaAcoes()
                        : "O botão abre o registro com a decisão pronta para você confirmar no sistema."))
                .append("</p>");
    }

    private void botao(StringBuilder html, ConteudoEmail conteudo) {
        if (!StringUtils.hasText(conteudo.urlAcao())) {
            return;
        }
        String url = escapar(conteudo.urlAcao());
        html.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" ")
                .append("style=\"margin:24px 0 10px;\"><tr><td class=\"ces-botao\" ")
                .append("style=\"background-color:").append(AZUL_MARCA).append(";border-radius:6px;\">")
                .append("<a href=\"").append(url).append("\" style=\"display:inline-block;")
                .append("padding:13px 30px;font-size:14px;font-weight:600;color:#ffffff;")
                .append("text-decoration:none;letter-spacing:0.2px;border-radius:6px;\">")
                .append(escapar(rotuloAcao(conteudo))).append("</a></td></tr></table>");

        // O endereço em texto logo abaixo do botão salva quem lê no celular corporativo,
        // onde o clique às vezes abre o app errado e o caminho é copiar o link.
        html.append("<p class=\"ces-suave\" style=\"margin:0;font-size:11px;line-height:1.5;color:")
                .append(TEXTO_SUAVE).append(";word-break:break-all;\">")
                .append("Caso o botão não funcione, copie o endereço: ").append(url).append("</p>");
    }

    private void anexos(StringBuilder html, ConteudoEmail conteudo) {
        if (conteudo.anexos().isEmpty()) {
            return;
        }
        html.append("<div style=\"margin-top:26px;padding-top:18px;border-top:1px solid ")
                .append(BORDA).append(";\">")
                .append("<p class=\"ces-suave\" style=\"margin:0 0 10px;font-size:11px;")
                .append("font-weight:700;text-transform:uppercase;letter-spacing:1px;color:")
                .append(TEXTO_SUAVE).append(";\">Anexos</p>");

        for (ConteudoEmail.Anexo anexo : conteudo.anexos()) {
            html.append("<p class=\"ces-corpo\" style=\"margin:0 0 6px;font-size:13px;")
                    .append("line-height:1.5;color:").append(TEXTO_CORPO).append(";\">")
                    .append(escapar(anexo.nomeArquivo()))
                    .append("<span class=\"ces-suave\" style=\"color:")
                    .append(TEXTO_SUAVE).append(";\"> &#8212; ")
                    .append(formatarTamanho(anexo.tamanhoBytes()));
            if (!anexo.anexado()) {
                html.append(", não anexado por exceder o limite; disponível no sistema");
            }
            html.append("</span></p>");
        }
        html.append("</div>");
    }

    /**
     * Rodapé factual. A versão anterior dizia "você recebeu porque acompanha avisos
     * de..." — a fórmula exata do rodapé de lista de marketing. Um sistema interno não
     * justifica ter escrito a quem trabalha na empresa; informa o que é e onde ajustar.
     */
    private void rodape(StringBuilder html) {
        html.append("<tr><td class=\"ces-superficie\" style=\"padding:16px 32px;background-color:")
                .append(FUNDO_FICHA)
                .append(";border-top:1px solid ").append(BORDA).append(";\">");

        html.append("<p class=\"ces-suave\" style=\"margin:0;font-size:11px;line-height:1.6;color:")
                .append(TEXTO_SUAVE)
                .append(";\">Mensagem automática do sistema Nexo. Não responda a este e-mail.");

        String preferencias = propriedades.resolverUrl("/notificacoes/preferencias");
        if (preferencias != null) {
            html.append("<br><a href=\"").append(escapar(preferencias)).append("\" style=\"color:")
                    .append(AZUL_LINK).append(";text-decoration:underline;\">")
                    .append("Preferências de notificação</a>");
        }
        html.append("</p></td></tr>");
    }

    /** "Aprovações · Prioridade alta" em uma linha, para a versão em texto puro. */
    private static String identificacao(ConteudoEmail conteudo) {
        boolean temEtiqueta = StringUtils.hasText(conteudo.etiqueta());
        boolean temPrioridade = StringUtils.hasText(conteudo.prioridade());

        if (temEtiqueta && temPrioridade) {
            return conteudo.etiqueta() + " · " + conteudo.prioridade();
        }
        if (temEtiqueta) {
            return conteudo.etiqueta();
        }
        return temPrioridade ? conteudo.prioridade() : "";
    }

    private static String rotuloAcao(ConteudoEmail conteudo) {
        return StringUtils.hasText(conteudo.rotuloAcao())
                ? conteudo.rotuloAcao() : "Acessar o registro";
    }

    /** Primeiro parágrafo, ou o título quando não há texto — nunca vazio. */
    private static String previa(ConteudoEmail conteudo) {
        String primeiro = conteudo.paragrafos().isEmpty() ? null : conteudo.paragrafos().get(0);
        return StringUtils.hasText(primeiro) ? primeiro : conteudo.titulo();
    }

    static String formatarTamanho(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.of("pt", "BR"), "%.0f KB", bytes / 1024d);
        }
        return String.format(Locale.of("pt", "BR"), "%.1f MB", bytes / (1024d * 1024d));
    }

    /**
     * Todo valor interpolado passa por aqui. O corpo da notificação vem de um template
     * que um administrador escreveu e de dados que um usuário digitou — sem escape,
     * um assunto de chamado com {@code <script>} viajaria intacto para a caixa de
     * quem recebe.
     */
    static String escapar(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
