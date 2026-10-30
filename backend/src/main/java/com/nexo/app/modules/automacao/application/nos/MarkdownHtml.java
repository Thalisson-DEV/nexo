package com.nexo.app.modules.automacao.application.nos;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown (o que o Copiloto escreve) em HTML de e-mail: estilos inline, tabelas com
 * borda simples, nada que o Outlook ignore. Cobre títulos, parágrafos, negrito,
 * itálico, código, links, listas, tabelas, citações e linhas horizontais. O texto é
 * escapado antes: marcação HTML vinda do modelo nunca passa crua.
 */
public final class MarkdownHtml {

    private static final String COR = "#334155";
    private static final String FORTE = "#0f172a";
    private static final String BORDA = "#e2e8f0";
    private static final String FUNDO = "#f8fafc";
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)]\\((https?://[^)\\s]+)\\)");
    private static final Pattern ORDENADA = Pattern.compile("^\\s*\\d+[.)]\\s+(.*)$");
    private static final Pattern MARCADOR = Pattern.compile("^\\s*[-*+•]\\s+(.*)$");

    private MarkdownHtml() {
    }

    public static String converter(String markdown) {
        if (markdown == null || markdown.isBlank()) return "";
        String[] linhas = markdown.replace("\r\n", "\n").split("\n");
        StringBuilder html = new StringBuilder();
        List<String> paragrafo = new ArrayList<>();
        int i = 0;
        while (i < linhas.length) {
            String linha = linhas[i];
            String t = linha.strip();
            if (t.isEmpty()) {
                fecharParagrafo(html, paragrafo);
                i++;
                continue;
            }
            if (t.startsWith("```")) {
                fecharParagrafo(html, paragrafo);
                StringBuilder codigo = new StringBuilder();
                i++;
                while (i < linhas.length && !linhas[i].strip().startsWith("```")) codigo.append(escapar(linhas[i])).append('\n');
                i++;
                html.append("<pre style=\"margin:8px 0 14px;padding:10px 12px;background:").append(FUNDO)
                        .append(";border:1px solid ").append(BORDA).append(";font-size:12px;white-space:pre-wrap;\">")
                        .append(codigo).append("</pre>");
                continue;
            }
            Matcher titulo = Pattern.compile("^(#{1,4})\\s+(.*)$").matcher(t);
            if (titulo.matches()) {
                fecharParagrafo(html, paragrafo);
                int nivel = titulo.group(1).length();
                int tamanho = switch (nivel) {
                    case 1 -> 20;
                    case 2 -> 17;
                    case 3 -> 15;
                    default -> 14;
                };
                html.append("<h").append(Math.min(nivel + 1, 4)).append(" style=\"margin:18px 0 8px;font-size:").append(tamanho)
                        .append("px;line-height:1.3;color:").append(FORTE).append(";\">").append(inline(titulo.group(2)))
                        .append("</h").append(Math.min(nivel + 1, 4)).append('>');
                i++;
                continue;
            }
            if (t.matches("^(-{3,}|\\*{3,}|_{3,})$")) {
                fecharParagrafo(html, paragrafo);
                html.append("<hr style=\"border:none;border-top:1px solid ").append(BORDA).append(";margin:16px 0;\">");
                i++;
                continue;
            }
            if (t.startsWith("|") && i + 1 < linhas.length && linhas[i + 1].strip().matches("^\\|?\\s*:?-{2,}.*")) {
                fecharParagrafo(html, paragrafo);
                List<String> cabecalho = celulas(t);
                i += 2;
                html.append("<table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\" style=\"border-collapse:collapse;width:100%;margin:6px 0 16px;\"><thead><tr>");
                for (String c : cabecalho) {
                    html.append("<th style=\"padding:7px 9px;border-bottom:2px solid #cbd5e1;font-size:12px;text-align:left;color:#64748b;\">")
                            .append(inline(c)).append("</th>");
                }
                html.append("</tr></thead><tbody>");
                while (i < linhas.length && linhas[i].strip().startsWith("|")) {
                    html.append("<tr>");
                    for (String c : celulas(linhas[i].strip())) {
                        html.append("<td style=\"padding:7px 9px;border-bottom:1px solid ").append(BORDA)
                                .append(";font-size:13px;color:").append(COR).append(";vertical-align:top;\">").append(inline(c)).append("</td>");
                    }
                    html.append("</tr>");
                    i++;
                }
                html.append("</tbody></table>");
                continue;
            }
            if (MARCADOR.matcher(linha).matches() || ORDENADA.matcher(linha).matches()) {
                fecharParagrafo(html, paragrafo);
                boolean ordenada = ORDENADA.matcher(linha).matches();
                html.append(ordenada ? "<ol" : "<ul").append(" style=\"margin:4px 0 14px;padding-left:22px;color:").append(COR).append(";\">");
                while (i < linhas.length) {
                    Matcher m = (ordenada ? ORDENADA : MARCADOR).matcher(linhas[i]);
                    if (!m.matches()) break;
                    html.append("<li style=\"margin:3px 0;font-size:14px;line-height:1.5;\">").append(inline(m.group(1))).append("</li>");
                    i++;
                }
                html.append(ordenada ? "</ol>" : "</ul>");
                continue;
            }
            if (t.startsWith(">")) {
                fecharParagrafo(html, paragrafo);
                StringBuilder citacao = new StringBuilder();
                while (i < linhas.length && linhas[i].strip().startsWith(">")) {
                    citacao.append(inline(linhas[i].strip().substring(1).strip())).append("<br>");
                    i++;
                }
                html.append("<div style=\"margin:8px 0 14px;padding:10px 14px;border-left:3px solid #1E3A8A;background:")
                        .append(FUNDO).append(";color:").append(COR).append(";font-size:14px;line-height:1.5;\">").append(citacao).append("</div>");
                continue;
            }
            paragrafo.add(t);
            i++;
        }
        fecharParagrafo(html, paragrafo);
        return html.toString();
    }

    private static void fecharParagrafo(StringBuilder html, List<String> paragrafo) {
        if (paragrafo.isEmpty()) return;
        List<String> partes = new ArrayList<>();
        for (String p : paragrafo) partes.add(inline(p));
        html.append("<p style=\"margin:0 0 12px;font-size:14px;line-height:1.6;color:").append(COR).append(";\">")
                .append(String.join("<br>", partes)).append("</p>");
        paragrafo.clear();
    }

    private static List<String> celulas(String linha) {
        String t = linha.strip();
        if (t.startsWith("|")) t = t.substring(1);
        if (t.endsWith("|")) t = t.substring(0, t.length() - 1);
        List<String> c = new ArrayList<>();
        for (String parte : t.split("\\|", -1)) c.add(parte.strip());
        return c;
    }

    static String inline(String texto) {
        String t = escapar(texto);
        t = t.replaceAll("`([^`]+)`", "<code style=\"background:" + FUNDO + ";padding:1px 4px;font-size:12px;\">$1</code>");
        t = t.replaceAll("\\*\\*([^*]+)\\*\\*", "<strong style=\"color:" + FORTE + ";\">$1</strong>");
        t = t.replaceAll("__([^_]+)__", "<strong style=\"color:" + FORTE + ";\">$1</strong>");
        t = t.replaceAll("(?<![*\\w])\\*([^*\\n]+)\\*(?!\\*)", "<em>$1</em>");
        Matcher m = LINK.matcher(t);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement("<a href=\"" + m.group(2) + "\" style=\"color:#2563eb;\">" + m.group(1) + "</a>"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static String escapar(String texto) {
        if (texto == null) return "";
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
