package com.nexo.app.modules.automacao.application.nos;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecNo;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecParam;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecSaida;
import com.nexo.app.modules.automacao.application.motor.Catalogo.ProvedorNos;
import com.nexo.app.modules.automacao.application.motor.ContextoAcao;
import com.nexo.app.modules.automacao.application.motor.ErroAcao;
import com.nexo.app.modules.automacao.application.usecases.ResolvedorDestinatarios;
import com.nexo.app.modules.automacao.domain.ArquivoAutomacao;
import com.nexo.app.modules.automacao.domain.TipoAutomacao;
import com.nexo.app.modules.automacao.domain.expressao.ErroExpressao;
import com.nexo.app.modules.automacao.domain.expressao.Expressoes;
import com.nexo.app.modules.automacao.domain.expressao.Valores;
import com.nexo.app.modules.automacao.infrastructure.persistence.ArquivoAutomacaoRepository;
import com.nexo.app.modules.automacao.infrastructure.persistence.SilencioAutomacaoRepository;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.notificacao.AvisoAutomacao;
import com.nexo.app.modules.notificacao.NotificacaoApi;
import com.nexo.app.shared.infrastructure.email.AnexoEmail;
import com.nexo.app.shared.infrastructure.email.ConteudoEmail;
import com.nexo.app.shared.infrastructure.email.DestaqueEmail;
import com.nexo.app.shared.infrastructure.email.EmailProperties;
import com.nexo.app.shared.infrastructure.email.MensagemEmail;
import com.nexo.app.shared.infrastructure.email.MontadorEmailHtml;
import com.nexo.app.shared.infrastructure.email.ResultadoEnvioEmail;
import com.nexo.app.shared.infrastructure.email.ServicoEmail;

/**
 * Avisos: e-mail com corpo rico e aviso no sininho (com e-mail opcional).
 *
 * <p>O corpo do e-mail vem do editor: texto formatado com <em>dados</em> (fichas
 * {@code <span data-expr>}) e <em>blocos</em> ({@code <div data-block data-source>}: tabela de
 * uma lista, texto em Markdown como a análise do Copiloto, HTML pronto de outro passo).
 * Tudo é sanitizado e entra no layout institucional de e-mail do Nexo.</p>
 */
@Component
public class NosAvisos implements ProvedorNos {

    private static final Safelist PERMITIDO = Safelist.relaxed()
            .addAttributes(":all", "style", "align", "width", "cellpadding", "cellspacing", "role", "border")
            .addProtocols("a", "href", "https", "http", "mailto");

    private final ResolvedorDestinatarios destinatarios;
    private final ServicoEmail email;
    private final MontadorEmailHtml montador;
    private final EmailProperties emailProperties;
    private final NotificacaoApi notificacoes;
    private final UsuarioApi usuarios;
    private final ArquivoAutomacaoRepository arquivos;
    private final SilencioAutomacaoRepository silencios;

    public NosAvisos(ResolvedorDestinatarios destinatarios, ServicoEmail email, MontadorEmailHtml montador,
                     EmailProperties emailProperties, NotificacaoApi notificacoes, UsuarioApi usuarios,
                     ArquivoAutomacaoRepository arquivos, SilencioAutomacaoRepository silencios) {
        this.destinatarios = destinatarios;
        this.email = email;
        this.montador = montador;
        this.emailProperties = emailProperties;
        this.notificacoes = notificacoes;
        this.usuarios = usuarios;
        this.arquivos = arquivos;
        this.silencios = silencios;
    }

    static List<List<String>> opcoes(Map<String, String> mapa) {
        List<List<String>> lista = new ArrayList<>();
        mapa.forEach((k, v) -> lista.add(List.of(k, v)));
        return lista;
    }

    @Override
    public Collection<EspecNo> nos() {
        List<EspecNo> nos = new ArrayList<>();
        nos.add(EspecNo.de("email.send", "notify", "Enviar e-mail",
                        "E-mail com a identidade do Nexo: texto formatado, dados do fluxo, tabelas, a análise do Copiloto e anexos.", "mail")
                .params(
                        EspecParam.de("to", "Para", "recipients").obrigatorio().padrao(List.of()).criar(),
                        EspecParam.de("cc", "Cópia (Cc)", "recipients").padrao(List.of()).avancado().criar(),
                        EspecParam.de("subject", "Assunto", "text").obrigatorio().placeholder("Ex.: Desconto validado aguardando baixa").criar(),
                        EspecParam.de("body", "Mensagem", "html").obrigatorio().semResolver().criar(),
                        EspecParam.de("attachments", "Anexos", "expression")
                                .placeholder("Escolha arquivos de passos anteriores (ex.: o PDF do Copiloto)").criar(),
                        EspecParam.de("label", "Área (linha acima do título)", "text").placeholder("Ex.: Recursos Humanos").avancado().criar(),
                        EspecParam.de("button_label", "Texto do botão", "text").placeholder("Abrir no Nexo").avancado().criar(),
                        EspecParam.de("button_link", "Link do botão", "text").placeholder("/descontos").avancado().criar(),
                        EspecParam.de("tone", "Destaque", "select").padrao("NEUTRO").estatico().avancado()
                                .opcoes(List.of(List.of("NEUTRO", "Neutro"), List.of("INFORMATIVO", "Informativo"),
                                        List.of("ATENCAO", "Atenção"), List.of("CRITICO", "Crítico"), List.of("POSITIVO", "Positivo"))).criar())
                .saidas(EspecSaida.de("subject", "Assunto enviado", "string"), EspecSaida.de("to", "Destinatários", "array"),
                        EspecSaida.de("delivery", "Entrega (ENVIADO / NAO_CONFIGURADO / FALHOU)", "string"),
                        EspecSaida.de("message_id", "Id no provedor", "string"), EspecSaida.de("html", "E-mail montado (HTML)", "string"))
                .executor(this::enviarEmail)
                .efeitoExterno()
                .repeticao("exponential", 3, 30)
                .satisfaz(TipoAutomacao.EMAIL, TipoAutomacao.ALERTA)
                .resumo(p -> Valores.texto(p.get("subject")))
                .palavras("email", "e-mail", "mensagem", "outlook", "enviar", "relatório")
                .criar());
        nos.add(EspecNo.de("notify.people", "notify", "Avisar pessoas",
                        "Aviso no sininho do Nexo (em tempo real) e, se quiser, também por e-mail — respeitando as preferências de cada pessoa.", "bell")
                .params(
                        EspecParam.de("recipients", "Quem recebe", "recipients").obrigatorio().padrao(List.of()).criar(),
                        EspecParam.de("title", "Título", "text").obrigatorio().placeholder("Desconto aguardando baixa").criar(),
                        EspecParam.de("body", "Texto", "textarea").placeholder("O desconto de {{ ... }} foi validado.").criar(),
                        EspecParam.de("link", "Ao clicar, abrir", "text").placeholder("/descontos").criar(),
                        EspecParam.de("email", "Enviar também por e-mail", "boolean").padrao(true).estatico().criar(),
                        EspecParam.de("category", "Categoria", "select").padrao("OPERACIONAL").estatico().opcoes(opcoes(notificacoes.categorias())).criar(),
                        EspecParam.de("priority", "Prioridade", "select").padrao("NORMAL").estatico().opcoes(opcoes(notificacoes.prioridades()))
                                .ajuda("Crítica chega mesmo para quem silenciou a categoria.").criar(),
                        EspecParam.de("silence_minutes", "Não repetir o mesmo aviso por (minutos)", "number").padrao(0).faixa(0, 100_000).avancado().criar(),
                        EspecParam.de("silence_key", "Assunto do silêncio", "text").padrao("{{ trigger.outputs.id_referencia }}").avancado()
                                .ajuda("Avisos com o mesmo assunto não se repetem dentro do intervalo acima.").criar())
                .saidas(EspecSaida.de("delivered", "Avisos entregues", "number"), EspecSaida.de("users", "Pessoas avisadas", "array"),
                        EspecSaida.de("external_emails", "E-mails externos", "array"), EspecSaida.de("silenced", "Silenciado", "boolean"))
                .executor(this::avisar)
                .efeitoExterno()
                .repeticao("exponential", 2, 15)
                .satisfaz(TipoAutomacao.ALERTA)
                .resumo(p -> Valores.texto(p.get("title")))
                .palavras("sino", "sininho", "notificação", "avisar", "alerta", "notificar")
                .criar());
        return nos;
    }

    // ------------------------------------------------------------------------------ e-mail

    private Map<String, Object> enviarEmail(ContextoAcao ctx, Map<String, Object> p) {
        ResolvedorDestinatarios.Resolucao paraR = destinatarios.resolverTudo(p.get("to"), ctx);
        ResolvedorDestinatarios.Resolucao ccR = destinatarios.resolverTudo(p.get("cc"), ctx);
        Set<String> para = destinatarios.emails(paraR);
        Set<String> copia = destinatarios.emails(ccR);
        copia.removeAll(para);
        String assunto = Valores.texto(p.get("subject")).strip();
        if (assunto.isEmpty()) throw new ErroAcao("Informe o assunto");
        List<String> reais = null;
        if (ctx.teste()) {
            if (ctx.emailTestador() == null || ctx.emailTestador().isBlank()) {
                throw new ErroAcao("Teste sem e-mail de quem testa: cadastre um e-mail no seu usuário");
            }
            reais = new ArrayList<>(para);
            para = new LinkedHashSet<>(List.of(ctx.emailTestador().toLowerCase()));
            copia = new LinkedHashSet<>();
            assunto = "[Teste] " + assunto;
        }
        if (para.isEmpty()) {
            throw new ErroAcao("Nenhum destinatário com e-mail em 'Para'" + (paraR.avisos().isEmpty() ? "" : " (" + String.join("; ", paraR.avisos()) + ")"));
        }
        String corpo = montarCorpo(Valores.texto(p.get("body")), ctx);
        if (Jsoup.parse(corpo).text().isBlank()) throw new ErroAcao("O texto do e-mail está vazio");
        List<AnexoEmail> anexos = new ArrayList<>();
        List<ConteudoEmail.Anexo> citados = new ArrayList<>();
        long limite = emailProperties.getLimiteAnexosBytes();
        long total = 0;
        for (Object ref : Valores.lista(p.get("attachments"))) {
            Map<String, Object> m = Valores.mapa(ref);
            String id = Valores.texto(m.isEmpty() ? ref : m.get("id"));
            if (id.isBlank()) continue;
            ArquivoAutomacao arquivo;
            try {
                arquivo = arquivos.findById(UUID.fromString(id)).orElse(null);
            } catch (IllegalArgumentException e) {
                arquivo = null;
            }
            if (arquivo == null) throw new ErroAcao("Anexo não encontrado: " + id);
            boolean cabe = total + arquivo.getTamanhoBytes() <= limite;
            if (cabe) {
                anexos.add(new AnexoEmail(arquivo.getNome(), arquivo.getTipoConteudo(), arquivo.getConteudo()));
                total += arquivo.getTamanhoBytes();
            }
            citados.add(new ConteudoEmail.Anexo(arquivo.getNome(), arquivo.getTamanhoBytes(), cabe));
        }
        ConteudoEmail conteudo = conteudo(Valores.texto(p.get("label")), Valores.texto(p.get("subject")),
                Valores.texto(p.getOrDefault("tone", "NEUTRO")), corpo, Valores.texto(p.get("button_label")),
                Valores.texto(p.get("button_link")).strip(), citados);
        String html = montador.montar(conteudo);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("subject", assunto);
        List<String> todos = new ArrayList<>(para);
        todos.addAll(copia);
        r.put("to", todos);
        r.put("html", html);
        if (reais != null) r.put("destinatarios_reais", reais);
        if (!email.ativo()) {
            r.put("delivery", "NAO_CONFIGURADO");
            r.put("message_id", null);
            r.put("message", "E-mail desligado neste ambiente: o conteúdo ficou guardado na execução.");
            return r;
        }
        Set<String> destino = new LinkedHashSet<>(para);
        destino.addAll(copia);
        ResultadoEnvioEmail resultado = email.enviar(MensagemEmail.builder().destinatarios(destino).assunto(assunto)
                .html(html).texto(montador.montarTexto(conteudo)).anexos(anexos).build());
        if (!resultado.sucesso()) {
            throw new ErroAcao("O provedor recusou o envio: " + resultado.erro(), true);
        }
        r.put("delivery", "ENVIADO");
        r.put("message_id", resultado.idExterno());
        return r;
    }

    /** O e-mail nos termos do layout institucional: o mesmo para o envio e para a prévia. */
    private ConteudoEmail conteudo(String area, String titulo, String tom, String corpo, String rotuloBotao, String link,
                                   List<ConteudoEmail.Anexo> citados) {
        DestaqueEmail destaque;
        try {
            destaque = DestaqueEmail.valueOf(tom == null || tom.isBlank() ? "NEUTRO" : tom);
        } catch (IllegalArgumentException e) {
            destaque = DestaqueEmail.NEUTRO;
        }
        return ConteudoEmail.builder()
                .etiqueta(area == null || area.isBlank() ? null : area.strip())
                .destaque(destaque)
                .titulo(titulo == null ? "" : titulo.strip())
                .htmlLivre(corpo)
                .rotuloAcao(link.isEmpty() ? null : (rotuloBotao == null || rotuloBotao.isBlank() ? "Abrir no Nexo" : rotuloBotao))
                .urlAcao(link.isEmpty() ? null : emailProperties.resolverUrl(link))
                .anexos(citados)
                .build();
    }

    // ------------------------------------------------------------------------------ prévia

    /** Como o e-mail vai chegar: assunto e HTML final, com os dados marcados. */
    public record Previa(String assunto, String html) {
    }

    private static final String MARCA_DADO = "background-color:#eef2ff;color:#1E3A8A;padding:0 4px;font-weight:600;";
    private static final String CAIXA_EXEMPLO = "margin:8px 0 16px;padding:14px 16px;border:1px dashed #94a3b8;background-color:#f8fafc;"
            + "font-size:13px;line-height:1.5;color:#64748b;";

    /**
     * A prévia do editor: o mesmo layout institucional do envio (logo, cabeçalho, botão,
     * rodapé), sem consultar nada. Cada dado do fluxo aparece marcado com o nome que o
     * editor mostra ({@code rotulos}: expressão → nome) e cada bloco, com um exemplo do
     * que ele monta.
     */
    public Previa previa(Map<String, Object> p, Map<String, String> rotulos) {
        Map<String, String> nomes = rotulos == null ? Map.of() : rotulos;
        Function<String, String> marcar = expr -> "[" + nomes.getOrDefault(expr.strip(), expr.strip()) + "]";
        String assunto = Expressoes.substituir(Valores.texto(p.get("subject")), marcar).strip();
        String area = Expressoes.substituir(Valores.texto(p.get("label")), marcar);
        Document doc = Jsoup.parseBodyFragment(Valores.texto(p.get("body")));
        for (Element span : doc.select("span[data-expr]")) {
            Element marca = new Element("span").attr("style", MARCA_DADO).text(marcar.apply(span.attr("data-expr")));
            span.replaceWith(marca);
        }
        for (Element bloco : doc.select("[data-block]")) {
            String fonte = bloco.attr("data-source");
            String origem = fonte.isBlank() ? "" : nomes.getOrDefault(fonte.strip(), fonte.strip());
            bloco.after(exemploBloco(bloco.attr("data-block"), origem, bloco.attr("data-label")));
            bloco.remove();
        }
        for (Element e : doc.body().getAllElements()) {
            for (TextNode texto : e.textNodes()) {
                if (texto.getWholeText().contains("{{")) texto.text(Expressoes.substituir(texto.getWholeText(), marcar));
            }
        }
        estilizar(doc);
        String corpo = Jsoup.clean(doc.body().html(), PERMITIDO);
        if (Jsoup.parse(corpo).text().isBlank()) {
            corpo = "<p style=\"" + CAIXA_EXEMPLO + "\">A mensagem ainda está vazia.</p>";
        }
        String link = Valores.texto(p.get("button_link")).strip();
        if (link.contains("{{")) link = "/";
        List<ConteudoEmail.Anexo> anexos = Valores.texto(p.get("attachments")).isBlank() ? List.of()
                : List.of(new ConteudoEmail.Anexo("relatorio.pdf (exemplo)", 240_000, true));
        ConteudoEmail conteudo = conteudo(area, assunto.isEmpty() ? "Assunto do e-mail" : assunto,
                Valores.texto(p.getOrDefault("tone", "NEUTRO")), corpo, Valores.texto(p.get("button_label")), link, anexos);
        return new Previa(assunto, montador.montar(conteudo));
    }

    private String exemploBloco(String tipo, String origem, String rotuloBotao) {
        String de = origem.isBlank() ? "" : " de <strong>" + MarkdownHtml.escapar(origem) + "</strong>";
        return switch (tipo) {
            case "markdown" -> "<div style=\"" + CAIXA_EXEMPLO + "\">Aqui entra o texto" + de
                    + ", já formatado: títulos, listas e tabelas (por exemplo, a análise do Copiloto).</div>";
            case "table" -> "<p style=\"margin:8px 0 4px;font-size:12px;color:#64748b;\">Tabela" + de + " (exemplo)</p>"
                    + "<table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\" style=\"border-collapse:collapse;width:100%;margin:0 0 16px\">"
                    + "<thead><tr>" + "<th style=\"padding:7px 9px;border-bottom:2px solid #cbd5e1;font-size:12px;text-align:left;color:#64748b\">Coluna</th>".repeat(3)
                    + "</tr></thead><tbody>"
                    + ("<tr>" + "<td style=\"padding:7px 9px;border-bottom:1px solid #e2e8f0;font-size:13px;color:#94a3b8\">valor</td>".repeat(3) + "</tr>").repeat(3)
                    + "</tbody></table>";
            case "list" -> "<p style=\"margin:8px 0 4px;font-size:12px;color:#64748b;\">Lista" + de + " (exemplo)</p>"
                    + "<ul style=\"margin:4px 0 14px;padding-left:22px;color:#94a3b8;\"><li>Primeiro item</li><li>Segundo item</li><li>…</li></ul>";
            case "html" -> "<div style=\"" + CAIXA_EXEMPLO + "\">Aqui entra a tabela pronta" + de + ".</div>";
            case "button" -> botao(rotuloBotao, "/");
            default -> "";
        };
    }

    /** O HTML do editor com os dados e os blocos resolvidos, sanitizado. */
    String montarCorpo(String html, ContextoAcao ctx) {
        Document doc = Jsoup.parseBodyFragment(html == null ? "" : html);
        for (Element span : doc.select("span[data-expr]")) {
            String valor;
            try {
                valor = Valores.texto(Expressoes.avaliar(span.attr("data-expr"), ctx.escopo()));
            } catch (ErroExpressao e) {
                valor = "";
            }
            span.replaceWith(new TextNode(valor));
        }
        for (Element bloco : doc.select("[data-block]")) {
            String tipo = bloco.attr("data-block");
            Object valor;
            try {
                valor = bloco.attr("data-source").isBlank() ? null : Expressoes.avaliar(bloco.attr("data-source"), ctx.escopo());
            } catch (ErroExpressao e) {
                throw new ErroAcao("Bloco do e-mail: " + e.getMessage());
            }
            String conteudo = switch (tipo) {
                case "markdown" -> MarkdownHtml.converter(Valores.texto(valor));
                case "html" -> Jsoup.clean(Valores.texto(valor), PERMITIDO);
                case "table" -> tabela(valor, ctx);
                case "list" -> lista(valor);
                case "button" -> botao(bloco.attr("data-label"), Valores.texto(valor));
                default -> "";
            };
            bloco.after(conteudo);
            bloco.remove();
        }
        // {{ expressões }} digitadas como texto.
        for (TextNode texto : doc.body().textNodes()) substituirTexto(texto, ctx);
        for (Element e : doc.body().getAllElements()) for (TextNode texto : e.textNodes()) substituirTexto(texto, ctx);
        estilizar(doc);
        return Jsoup.clean(doc.body().html(), PERMITIDO);
    }

    private static void substituirTexto(TextNode texto, ContextoAcao ctx) {
        String t = texto.getWholeText();
        if (!t.contains("{{")) return;
        texto.text(Expressoes.substituir(t, expr -> {
            try {
                return Valores.texto(Expressoes.avaliar(expr, ctx.escopo()));
            } catch (ErroExpressao e) {
                return "";
            }
        }));
    }

    private static void estilizar(Document doc) {
        for (Element p : doc.select("p:not([style])")) p.attr("style", "margin:0 0 12px;font-size:14px;line-height:1.6;color:#334155;");
        for (Element h : doc.select("h1:not([style]),h2:not([style])")) h.attr("style", "margin:18px 0 8px;font-size:17px;line-height:1.3;color:#0f172a;");
        for (Element h : doc.select("h3:not([style]),h4:not([style])")) h.attr("style", "margin:16px 0 6px;font-size:15px;line-height:1.3;color:#0f172a;");
        for (Element l : doc.select("ul:not([style]),ol:not([style])")) l.attr("style", "margin:4px 0 14px;padding-left:22px;color:#334155;");
        for (Element a : doc.select("a:not([style])")) a.attr("style", "color:#2563eb;");
    }

    private static String tabela(Object valor, ContextoAcao ctx) {
        List<Object> itens = valor instanceof Map<?, ?> m && m.get("items") instanceof List<?> l ? Valores.lista(l) : Valores.lista(valor);
        if (itens.isEmpty()) return "<p style=\"margin:0 0 12px;font-size:13px;color:#64748b;\">Nenhum item.</p>";
        LinkedHashSet<String> chaves = new LinkedHashSet<>();
        for (Object item : itens.subList(0, Math.min(50, itens.size()))) {
            if (item instanceof Map<?, ?> m) m.forEach((k, v) -> {
                if (!(v instanceof Map<?, ?>) && !(v instanceof List<?>)) chaves.add(String.valueOf(k));
            });
        }
        List<String> colunas = chaves.stream().limit(10).toList();
        StringBuilder html = new StringBuilder("<table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\" style=\"border-collapse:collapse;width:100%;margin:4px 0 16px\"><thead><tr>");
        for (String c : colunas) {
            html.append("<th style=\"padding:7px 9px;border-bottom:2px solid #cbd5e1;font-size:12px;text-align:left;color:#64748b\">")
                    .append(MarkdownHtml.escapar(NosGatilhos.rotulo(c))).append("</th>");
        }
        html.append("</tr></thead><tbody>");
        for (Object item : itens.subList(0, Math.min(500, itens.size()))) {
            html.append("<tr>");
            for (String c : colunas) {
                html.append("<td style=\"padding:7px 9px;border-bottom:1px solid #e2e8f0;font-size:13px;color:#334155;vertical-align:top\">")
                        .append(MarkdownHtml.escapar(Valores.texto(NosDados.campo(item, c)))).append("</td>");
            }
            html.append("</tr>");
        }
        return html.append("</tbody></table>").toString();
    }

    private static String lista(Object valor) {
        StringBuilder html = new StringBuilder("<ul>");
        for (Object item : Valores.lista(valor)) {
            Object texto = item instanceof Map<?, ?> m ? (m.get("nome") != null ? m.get("nome") : m.get("key") != null ? m.get("key") : item) : item;
            html.append("<li>").append(MarkdownHtml.escapar(Valores.texto(texto))).append("</li>");
        }
        return html.append("</ul>").toString();
    }

    private String botao(String rotulo, String link) {
        if (link == null || link.isBlank()) return "";
        String url = emailProperties.resolverUrl(link);
        return "<p style=\"margin:16px 0;\"><a href=\"" + MarkdownHtml.escapar(url) + "\" style=\"display:inline-block;padding:10px 18px;"
                + "background:#1E3A8A;color:#ffffff;text-decoration:none;font-weight:600;font-size:14px;\">"
                + MarkdownHtml.escapar(rotulo == null || rotulo.isBlank() ? "Abrir" : rotulo) + "</a></p>";
    }

    // ------------------------------------------------------------------------------ sininho

    private Map<String, Object> avisar(ContextoAcao ctx, Map<String, Object> p) {
        String titulo = Valores.texto(p.get("title")).strip();
        if (titulo.isEmpty()) throw new ErroAcao("Informe o título do aviso");
        double silencio = Valores.dbl(p.get("silence_minutes"));
        String chaveSilencio = Valores.texto(p.get("silence_key")).strip();
        if (chaveSilencio.isEmpty()) chaveSilencio = "geral";
        if (chaveSilencio.length() > 200) chaveSilencio = chaveSilencio.substring(0, 200);
        OffsetDateTime agora = OffsetDateTime.now();
        Map<String, Object> r = new LinkedHashMap<>();
        if (silencio > 0 && !ctx.teste()) {
            OffsetDateTime ultimo = silencios.ultimoEnvio(ctx.automacaoId(), ctx.noId(), chaveSilencio);
            if (ultimo != null && ultimo.plusMinutes((long) silencio).isAfter(agora)) {
                r.put("delivered", 0);
                r.put("users", List.of());
                r.put("external_emails", List.of());
                r.put("silenced", true);
                return r;
            }
        }
        ResolvedorDestinatarios.Resolucao resolucao = destinatarios.resolverTudo(p.get("recipients"), ctx);
        Set<UUID> ids = new LinkedHashSet<>(resolucao.usuarios());
        Set<String> externos = new LinkedHashSet<>(resolucao.emailsExternos());
        if (ctx.teste()) {
            ids.clear();
            externos.clear();
            if (ctx.emailTestador() != null) usuarios.buscarIdAtivoPorEmail(ctx.emailTestador()).ifPresent(ids::add);
            if (ids.isEmpty()) throw new ErroAcao("No teste o aviso vai só para quem testa, e não encontrei o seu usuário pelo e-mail");
        }
        if (ids.isEmpty() && externos.isEmpty()) {
            throw new ErroAcao("Ninguém para avisar" + (resolucao.avisos().isEmpty() ? "" : ": " + String.join("; ", resolucao.avisos())));
        }
        boolean porEmail = !Boolean.FALSE.equals(p.get("email"));
        String corpo = Valores.texto(p.get("body"));
        String link = Valores.texto(p.get("link")).strip();
        int entregues = 0;
        if (!ids.isEmpty()) {
            Map<String, Object> contexto = new LinkedHashMap<>();
            contexto.put("idExecucao", ctx.execucaoId().toString());
            entregues = notificacoes.publicarAviso(new AvisoAutomacao(ids, ctx.automacaoId(), referencia(ctx),
                    Valores.texto(p.getOrDefault("category", "OPERACIONAL")), Valores.texto(p.getOrDefault("priority", "NORMAL")),
                    (ctx.teste() ? "[Teste] " : "") + titulo, corpo, link.isEmpty() ? null : link, contexto,
                    "automacao:" + ctx.automacaoId() + ":" + ctx.execucaoId() + ":" + ctx.noId() + ctx.iteracao(),
                    porEmail, ctx.teste() ? null : chaveDoEvento(ctx)));
        }
        if (porEmail && !externos.isEmpty() && email.ativo()) {
            ConteudoEmail conteudo = ConteudoEmail.builder().titulo(titulo)
                    .paragrafos(corpo.isBlank() ? List.of() : List.of(corpo.split("\\n")))
                    .rotuloAcao(link.isEmpty() ? null : "Abrir no Nexo").urlAcao(link.isEmpty() ? null : emailProperties.resolverUrl(link))
                    .build();
            ResultadoEnvioEmail resultado = email.enviar(MensagemEmail.builder().destinatarios(externos).assunto(titulo)
                    .html(montador.montar(conteudo)).texto(montador.montarTexto(conteudo)).build());
            if (!resultado.sucesso()) throw new ErroAcao("O provedor recusou o e-mail externo: " + resultado.erro(), true);
        }
        if (silencio > 0 && !ctx.teste()) silencios.registrar(ctx.automacaoId(), ctx.noId(), chaveSilencio, agora);
        r.put("delivered", entregues);
        r.put("users", destinatarios.descrever(ids));
        r.put("external_emails", new ArrayList<>(externos));
        r.put("silenced", false);
        return r;
    }

    /**
     * A chave do gatilho de evento ({@code event.desconto.validado} → {@code desconto.validado}).
     * Com ela o e-mail traz a ficha do registro e os botões que o módulo de origem monta.
     */
    private static String chaveDoEvento(ContextoAcao ctx) {
        String tipo = Valores.texto(Expressoes.obter(Expressoes.obter(ctx.escopo(), "trigger"), "type"));
        return tipo.startsWith("event.") ? tipo.substring("event.".length()) : null;
    }

    private static UUID referencia(ContextoAcao ctx) {
        Object id = Expressoes.obter(Expressoes.obter(Expressoes.obter(ctx.escopo(), "trigger"), "outputs"), "id_referencia");
        try {
            return id == null ? null : UUID.fromString(Valores.texto(id));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
