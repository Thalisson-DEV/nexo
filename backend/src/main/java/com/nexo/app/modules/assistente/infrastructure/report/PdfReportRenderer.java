package com.nexo.app.modules.assistente.infrastructure.report;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Renderizador de relatorios PDF do Copiloto com a identidade do Nexo: capa com metadados, resumo
 * executivo, KPIs, graficos vetoriais (barras, barras horizontais/empilhadas, linha, area, pizza, rosca,
 * dispersao), tabelas paginadas com cabecalho repetido, apendice de procedencia dos dados e rodape com
 * paginacao. Recebe componentes ja resolvidos pelo {@code ArtifactResolver}: nenhum numero vem do texto do
 * modelo. Usa as fontes padrao do PDF (Helvetica, WinAnsi), que cobrem o portugues; caracteres fora do
 * conjunto sao substituidos por equivalentes.
 */
@Component
public class PdfReportRenderer {

    private static final float MARGEM = 48;
    private static final float LARGURA_PAGINA = PDRectangle.A4.getWidth();
    private static final float ALTURA_PAGINA = PDRectangle.A4.getHeight();
    private static final float LARGURA_UTIL = LARGURA_PAGINA - 2 * MARGEM;
    private static final float TOPO_CONTEUDO = ALTURA_PAGINA - 64;
    private static final float BASE_CONTEUDO = 56;

    private static final Color MARCA = new Color(0x25, 0x63, 0xeb);
    private static final Color MARCA_ESCURA = new Color(0x1e, 0x3a, 0x8a);
    private static final Color MARCA_SUAVE = new Color(0xef, 0xf6, 0xff);
    private static final Color TEXTO = new Color(0x0f, 0x17, 0x2a);
    private static final Color TEXTO_SUAVE = new Color(0x47, 0x55, 0x69);
    private static final Color BORDA = new Color(0xe2, 0xe8, 0xf0);
    private static final Color ZEBRA = new Color(0xf8, 0xfa, 0xfc);
    private static final Color SUCESSO = new Color(0x15, 0x80, 0x3d);
    private static final Color PERIGO = new Color(0xb9, 0x1c, 0x1c);
    private static final Color[] PALETA = {
            new Color(0x25, 0x63, 0xeb), new Color(0x0e, 0xa5, 0xe9), new Color(0x16, 0xa3, 0x4a),
            new Color(0xf5, 0x9e, 0x0b), new Color(0xdc, 0x26, 0x26), new Color(0x7c, 0x3a, 0xed),
            new Color(0x0d, 0x94, 0x88), new Color(0xdb, 0x27, 0x77), new Color(0x64, 0x74, 0x8b)};

    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", PT_BR);
    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    public record Relatorio(String titulo, String subtitulo, String periodo, String autor, OffsetDateTime geradoEm,
            String resumo, List<Secao> secoes, String conclusoes, List<Map<String, Object>> procedencia) {
    }

    public record Secao(String titulo, String texto, List<Map<String, Object>> componentes) {
    }

    public byte[] renderizar(Relatorio relatorio) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Fontes fontes = new Fontes();
            Pagina pagina = new Pagina(document, fontes, relatorio.titulo());
            capa(pagina, relatorio);
            if (naoVazio(relatorio.resumo())) {
                pagina.subtitulo("Resumo executivo");
                caixaDestaque(pagina, relatorio.resumo());
            }
            for (Secao secao : relatorio.secoes()) {
                pagina.garantir(60);
                if (naoVazio(secao.titulo())) pagina.subtitulo(secao.titulo());
                if (naoVazio(secao.texto())) markdown(pagina, secao.texto());
                componentes(pagina, secao.componentes());
            }
            if (naoVazio(relatorio.conclusoes())) {
                pagina.garantir(60);
                pagina.subtitulo("Conclusões e recomendações");
                markdown(pagina, relatorio.conclusoes());
            }
            procedencia(pagina, relatorio.procedencia());
            pagina.fechar();
            rodapes(document, fontes, relatorio);
            PDDocumentInformation info = document.getDocumentInformation();
            info.setTitle(fontes.seguro(relatorio.titulo()));
            info.setAuthor("Copiloto (Nexo)");
            info.setCreator("Nexo - Copiloto");
            info.setSubject(fontes.seguro(Objects.toString(relatorio.subtitulo(), "")));
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("Falha ao gerar o PDF", exception);
        }
    }

    // ------------------------------------------------------------------------------------ estrutura

    private void capa(Pagina pagina, Relatorio relatorio) throws IOException {
        pagina.y = TOPO_CONTEUDO - 8;
        pagina.retangulo(MARGEM, pagina.y - 4, 44, 4, MARCA);
        pagina.y -= 30;
        for (String linha : pagina.fontes.quebrar(relatorio.titulo(), pagina.fontes.negrito, 22, LARGURA_UTIL)) {
            pagina.texto(linha, MARGEM, pagina.y, pagina.fontes.negrito, 22, TEXTO);
            pagina.y -= 28;
        }
        if (naoVazio(relatorio.subtitulo())) {
            for (String linha : pagina.fontes.quebrar(relatorio.subtitulo(), pagina.fontes.normal, 12, LARGURA_UTIL)) {
                pagina.texto(linha, MARGEM, pagina.y, pagina.fontes.normal, 12, TEXTO_SUAVE);
                pagina.y -= 16;
            }
        }
        pagina.y -= 6;
        List<String> meta = new ArrayList<>();
        if (naoVazio(relatorio.periodo())) meta.add("Período: " + relatorio.periodo());
        if (naoVazio(relatorio.autor())) meta.add("Solicitado por: " + relatorio.autor());
        meta.add("Gerado em: " + DATA_HORA.format(relatorio.geradoEm().atZoneSameInstant(ZONA)));
        for (String linha : pagina.fontes.quebrar(String.join("   |   ", meta), pagina.fontes.normal, 9, LARGURA_UTIL)) {
            pagina.texto(linha, MARGEM, pagina.y, pagina.fontes.normal, 9, TEXTO_SUAVE);
            pagina.y -= 13;
        }
        pagina.y -= 4;
        pagina.linha(MARGEM, pagina.y, MARGEM + LARGURA_UTIL, pagina.y, BORDA, 1);
        pagina.y -= 18;
    }

    private void caixaDestaque(Pagina pagina, String texto) throws IOException {
        List<Linha> linhas = pagina.fontes.markdown(texto, 10, LARGURA_UTIL - 24);
        float altura = alturaLinhas(linhas) + 20;
        if (altura < ALTURA_PAGINA * 0.6f) {
            pagina.garantir(altura);
            pagina.retangulo(MARGEM, pagina.y - altura, LARGURA_UTIL, altura, MARCA_SUAVE);
            pagina.retangulo(MARGEM, pagina.y - altura, 3, altura, MARCA);
            pagina.y -= 14;
            desenharLinhas(pagina, linhas, MARGEM + 14);
            pagina.y -= 12;
        } else {
            markdown(pagina, texto);
        }
    }

    private void markdown(Pagina pagina, String texto) throws IOException {
        desenharLinhas(pagina, pagina.fontes.markdown(texto, 10, LARGURA_UTIL), MARGEM);
        pagina.y -= 6;
    }

    private static float alturaLinhas(List<Linha> linhas) {
        float total = 0;
        for (Linha linha : linhas) total += linha.altura();
        return total;
    }

    private void desenharLinhas(Pagina pagina, List<Linha> linhas, float x) throws IOException {
        for (Linha linha : linhas) {
            pagina.garantir(linha.altura());
            if (!linha.texto().isEmpty()) {
                float deslocamento = linha.recuo();
                if (linha.marcador() != null) {
                    pagina.texto(linha.marcador(), x + deslocamento - 10, pagina.y - linha.tamanho(), pagina.fontes.normal,
                            linha.tamanho(), MARCA);
                }
                pagina.texto(linha.texto(), x + deslocamento, pagina.y - linha.tamanho(),
                        linha.negrito() ? pagina.fontes.negrito : pagina.fontes.normal, linha.tamanho(),
                        TEXTO);
            }
            pagina.y -= linha.altura();
        }
    }

    private void componentes(Pagina pagina, List<Map<String, Object>> componentes) throws IOException {
        if (componentes == null || componentes.isEmpty()) return;
        List<Map<String, Object>> kpis = new ArrayList<>();
        for (Map<String, Object> componente : componentes) {
            String tipo = Objects.toString(componente.get("tipo"), "");
            if (tipo.equals("kpi")) {
                kpis.add(componente);
                continue;
            }
            if (!kpis.isEmpty()) {
                kpis(pagina, kpis);
                kpis.clear();
            }
            switch (tipo) {
                case "grafico" -> grafico(pagina, componente);
                case "tabela" -> tabela(pagina, componente);
                case "texto" -> {
                    if (naoVazio(Objects.toString(componente.get("titulo"), null))) {
                        pagina.garantir(30);
                        pagina.texto(Objects.toString(componente.get("titulo")), MARGEM, pagina.y - 11, pagina.fontes.negrito, 11, TEXTO);
                        pagina.y -= 18;
                    }
                    markdown(pagina, Objects.toString(componente.get("conteudo"), ""));
                }
                default -> {
                    // tipo desconhecido: ignorado no PDF
                }
            }
        }
        if (!kpis.isEmpty()) kpis(pagina, kpis);
    }

    // ------------------------------------------------------------------------------------ KPIs

    private void kpis(Pagina pagina, List<Map<String, Object>> kpis) throws IOException {
        int porLinha = Math.min(4, kpis.size());
        float espaco = 10;
        float largura = (LARGURA_UTIL - espaco * (porLinha - 1)) / porLinha;
        float altura = 70;
        for (int inicio = 0; inicio < kpis.size(); inicio += porLinha) {
            pagina.garantir(altura + 12);
            float topo = pagina.y;
            for (int i = inicio; i < Math.min(kpis.size(), inicio + porLinha); i++) {
                Map<String, Object> kpi = kpis.get(i);
                float x = MARGEM + (i - inicio) * (largura + espaco);
                pagina.retangulo(x, topo - altura, largura, altura, ZEBRA);
                pagina.contorno(x, topo - altura, largura, altura, BORDA);
                pagina.retangulo(x, topo - 3, largura, 3, MARCA);
                String titulo = pagina.fontes.cortar(Objects.toString(kpi.get("titulo"), "").toUpperCase(PT_BR),
                        pagina.fontes.negrito, 7, largura - 16);
                pagina.texto(titulo, x + 8, topo - 17, pagina.fontes.negrito, 7, TEXTO_SUAVE);
                String formato = Objects.toString(kpi.get("formato"), "numero");
                String valor = pagina.fontes.cortar(formatar(kpi.get("valor"), formato, false), pagina.fontes.negrito, 17, largura - 16);
                pagina.texto(valor, x + 8, topo - 40, pagina.fontes.negrito, 17, TEXTO);
                if (kpi.get("comparacao") instanceof Map<?, ?> comparacao) {
                    Object variacao = comparacao.get("variacao");
                    String rotulo = Objects.toString(comparacao.get("rotulo"), "");
                    String texto;
                    Color cor = TEXTO_SUAVE;
                    if (variacao instanceof Number numero) {
                        double v = numero.doubleValue();
                        boolean melhorMenor = "menor".equals(kpi.get("melhorQuando"));
                        boolean bom = melhorMenor ? v < 0 : v > 0;
                        cor = v == 0 ? TEXTO_SUAVE : (bom ? SUCESSO : PERIGO);
                        texto = (v > 0 ? "+" : "") + formatar(v, "percentual", false) + " " + rotulo;
                    } else {
                        texto = "Referência: " + formatar(comparacao.get("valor"), formato, true) + " " + rotulo;
                    }
                    pagina.texto(pagina.fontes.cortar(texto, pagina.fontes.normal, 7.5f, largura - 16), x + 8, topo - 58,
                            pagina.fontes.normal, 7.5f, cor);
                }
            }
            pagina.y = topo - altura - 12;
        }
    }

    // ------------------------------------------------------------------------------------ graficos

    @SuppressWarnings("unchecked")
    private void grafico(Pagina pagina, Map<String, Object> grafico) throws IOException {
        String tipo = Objects.toString(grafico.get("grafico"), "barra");
        float altura = tipo.equals("barra_horizontal")
                ? Math.min(420, 60 + 16 * ((List<Object>) grafico.getOrDefault("rotulos", List.of())).size()) : 230;
        pagina.garantir(altura + 50);
        String titulo = Objects.toString(grafico.get("titulo"), "");
        pagina.texto(pagina.fontes.cortar(titulo, pagina.fontes.negrito, 11, LARGURA_UTIL), MARGEM, pagina.y - 11,
                pagina.fontes.negrito, 11, TEXTO);
        pagina.y -= 18;
        if (grafico.get("descricao") != null) {
            for (String linha : pagina.fontes.quebrar(grafico.get("descricao").toString(), pagina.fontes.normal, 8.5f, LARGURA_UTIL)) {
                pagina.texto(linha, MARGEM, pagina.y - 8.5f, pagina.fontes.normal, 8.5f, TEXTO_SUAVE);
                pagina.y -= 12;
            }
        }
        float topo = pagina.y;
        float base = topo - altura;
        switch (tipo) {
            case "pizza", "rosca" -> pizza(pagina, grafico, base, altura, tipo.equals("rosca"));
            case "dispersao" -> dispersao(pagina, grafico, base, altura);
            case "barra_horizontal" -> barrasHorizontais(pagina, grafico, base, altura);
            case "linha", "area" -> cartesiano(pagina, grafico, base, altura, false, tipo.equals("area"));
            default -> cartesiano(pagina, grafico, base, altura, true, false);
        }
        pagina.y = base - 8;
        Object observacao = grafico.get("observacao");
        String rodape = "Fonte: " + grafico.getOrDefault("resultado", "") + " (" + procedenciaLegivel(grafico.get("procedencia")) + ")"
                + (observacao == null ? "" : " - " + observacao);
        for (String linha : pagina.fontes.quebrar(rodape, pagina.fontes.normal, 7.5f, LARGURA_UTIL)) {
            pagina.texto(linha, MARGEM, pagina.y - 7.5f, pagina.fontes.normal, 7.5f, TEXTO_SUAVE);
            pagina.y -= 11;
        }
        pagina.y -= 10;
    }

    @SuppressWarnings("unchecked")
    private void cartesiano(Pagina pagina, Map<String, Object> grafico, float base, float altura, boolean barras,
            boolean area) throws IOException {
        List<String> rotulos = ((List<Object>) grafico.getOrDefault("rotulos", List.of())).stream().map(String::valueOf).toList();
        List<Map<String, Object>> series = (List<Map<String, Object>>) grafico.getOrDefault("series", List.of());
        boolean empilhado = Boolean.TRUE.equals(grafico.get("empilhado"));
        String formato = Objects.toString(grafico.get("formato"), "numero");
        if (rotulos.isEmpty() || series.isEmpty()) return;
        float legenda = series.size() > 1 ? 16 : 0;
        boolean rotacionar = barras ? rotulos.size() > 8 || rotulos.stream().anyMatch(r -> r.length() > 12) : rotulos.size() > 14;
        float alturaRotulos = rotacionar ? 52 : 16;
        float x0 = MARGEM + 46;
        float x1 = MARGEM + LARGURA_UTIL;
        float y0 = base + alturaRotulos + legenda;
        float y1 = base + altura - 6;

        double minimo = 0;
        double maximo = 0;
        for (int i = 0; i < rotulos.size(); i++) {
            double positivo = 0;
            double negativo = 0;
            for (Map<String, Object> serie : series) {
                double v = valor(serie, i);
                if (empilhado) {
                    if (v >= 0) positivo += v;
                    else negativo += v;
                } else {
                    maximo = Math.max(maximo, v);
                    minimo = Math.min(minimo, v);
                }
            }
            if (empilhado) {
                maximo = Math.max(maximo, positivo);
                minimo = Math.min(minimo, negativo);
            }
        }
        double[] escala = escala(minimo, maximo);
        eixoY(pagina, escala, x0, x1, y0, y1, formato);
        float faixa = (x1 - x0) / rotulos.size();
        float zero = (float) (y0 + (0 - escala[0]) / (escala[1] - escala[0]) * (y1 - y0));

        if (barras) {
            float larguraGrupo = faixa * 0.72f;
            for (int i = 0; i < rotulos.size(); i++) {
                float inicio = x0 + i * faixa + (faixa - larguraGrupo) / 2;
                double acumuladoPositivo = 0;
                double acumuladoNegativo = 0;
                for (int s = 0; s < series.size(); s++) {
                    double v = valor(series.get(s), i);
                    Color cor = PALETA[s % PALETA.length];
                    if (empilhado) {
                        double de = v >= 0 ? acumuladoPositivo : acumuladoNegativo;
                        double ate = de + v;
                        if (v >= 0) acumuladoPositivo = ate;
                        else acumuladoNegativo = ate;
                        float ya = posicao(de, escala, y0, y1);
                        float yb = posicao(ate, escala, y0, y1);
                        pagina.retangulo(inicio, Math.min(ya, yb), larguraGrupo, Math.abs(yb - ya), cor);
                    } else {
                        float largura = larguraGrupo / series.size();
                        float yv = posicao(v, escala, y0, y1);
                        pagina.retangulo(inicio + s * largura, Math.min(zero, yv), Math.max(0.5f, largura - 1), Math.abs(yv - zero), cor);
                    }
                }
            }
        } else {
            for (int s = 0; s < series.size(); s++) {
                Color cor = PALETA[s % PALETA.length];
                List<float[]> pontos = new ArrayList<>();
                for (int i = 0; i < rotulos.size(); i++) {
                    Object bruto = ((List<Object>) series.get(s).get("valores")).get(i);
                    if (!(bruto instanceof Number)) continue;
                    pontos.add(new float[]{x0 + i * faixa + faixa / 2, posicao(((Number) bruto).doubleValue(), escala, y0, y1)});
                }
                if (pontos.isEmpty()) continue;
                if (area && series.size() == 1) pagina.poligonoArea(pontos, zero, clarear(cor, 0.78f));
                pagina.polilinha(pontos, cor, 1.6f);
                if (pontos.size() <= 40) for (float[] ponto : pontos) pagina.ponto(ponto[0], ponto[1], 1.8f, cor);
            }
        }
        pagina.linha(x0, zero, x1, zero, TEXTO_SUAVE, 0.6f);
        rotulosX(pagina, rotulos, x0, faixa, y0, rotacionar);
        if (legenda > 0) legenda(pagina, series.stream().map(serie -> Objects.toString(serie.get("nome"), "")).toList(), base + 2);
    }

    @SuppressWarnings("unchecked")
    private void barrasHorizontais(Pagina pagina, Map<String, Object> grafico, float base, float altura) throws IOException {
        List<String> rotulos = ((List<Object>) grafico.getOrDefault("rotulos", List.of())).stream().map(String::valueOf).toList();
        List<Map<String, Object>> series = (List<Map<String, Object>>) grafico.getOrDefault("series", List.of());
        String formato = Objects.toString(grafico.get("formato"), "numero");
        if (rotulos.isEmpty() || series.isEmpty()) return;
        float legenda = series.size() > 1 ? 16 : 0;
        float larguraRotulo = 130;
        float x0 = MARGEM + larguraRotulo;
        float x1 = MARGEM + LARGURA_UTIL - 50;
        float y0 = base + legenda + 14;
        float y1 = base + altura;
        double maximo = 0;
        double minimo = 0;
        for (Map<String, Object> serie : series) {
            for (int i = 0; i < rotulos.size(); i++) {
                maximo = Math.max(maximo, valor(serie, i));
                minimo = Math.min(minimo, valor(serie, i));
            }
        }
        double[] escala = escala(minimo, maximo);
        float faixa = (y1 - y0) / rotulos.size();
        float zero = (float) (x0 + (0 - escala[0]) / (escala[1] - escala[0]) * (x1 - x0));
        for (double tick = escala[0]; tick <= escala[1] + escala[2] / 2; tick += escala[2]) {
            float x = (float) (x0 + (tick - escala[0]) / (escala[1] - escala[0]) * (x1 - x0));
            pagina.linha(x, y0, x, y1, BORDA, 0.5f);
            String texto = formatar(tick, formato, true);
            float w = pagina.fontes.largura(texto, pagina.fontes.normal, 7);
            pagina.texto(texto, x - w / 2, y0 - 10, pagina.fontes.normal, 7, TEXTO_SUAVE);
        }
        for (int i = 0; i < rotulos.size(); i++) {
            float topo = y1 - i * faixa;
            float alturaBarra = faixa * 0.7f / series.size();
            String rotulo = pagina.fontes.cortar(rotulos.get(i), pagina.fontes.normal, 7.5f, larguraRotulo - 8);
            float w = pagina.fontes.largura(rotulo, pagina.fontes.normal, 7.5f);
            pagina.texto(rotulo, x0 - 6 - w, topo - faixa / 2 - 2.5f, pagina.fontes.normal, 7.5f, TEXTO);
            for (int s = 0; s < series.size(); s++) {
                double v = valor(series.get(s), i);
                float xv = (float) (x0 + (v - escala[0]) / (escala[1] - escala[0]) * (x1 - x0));
                float yb = topo - faixa * 0.15f - (s + 1) * alturaBarra;
                pagina.retangulo(Math.min(zero, xv), yb, Math.max(0.5f, Math.abs(xv - zero)), alturaBarra - 0.8f, PALETA[s % PALETA.length]);
                if (series.size() == 1) {
                    pagina.texto(formatar(v, formato, true), Math.max(zero, xv) + 3, yb + alturaBarra / 2 - 2.5f,
                            pagina.fontes.normal, 7, TEXTO_SUAVE);
                }
            }
        }
        pagina.linha(zero, y0, zero, y1, TEXTO_SUAVE, 0.6f);
        if (legenda > 0) legenda(pagina, series.stream().map(serie -> Objects.toString(serie.get("nome"), "")).toList(), base + 2);
    }

    @SuppressWarnings("unchecked")
    private void pizza(Pagina pagina, Map<String, Object> grafico, float base, float altura, boolean rosca) throws IOException {
        List<String> rotulos = ((List<Object>) grafico.getOrDefault("rotulos", List.of())).stream().map(String::valueOf).toList();
        List<Map<String, Object>> series = (List<Map<String, Object>>) grafico.getOrDefault("series", List.of());
        if (rotulos.isEmpty() || series.isEmpty()) return;
        String formato = Objects.toString(grafico.get("formato"), "numero");
        Map<String, Object> serie = series.getFirst();
        double total = 0;
        for (int i = 0; i < rotulos.size(); i++) total += Math.max(0, valor(serie, i));
        if (total <= 0) return;
        float raio = Math.min(altura / 2 - 10, 100);
        float cx = MARGEM + raio + 20;
        float cy = base + altura / 2;
        double angulo = 0;
        for (int i = 0; i < rotulos.size(); i++) {
            double v = Math.max(0, valor(serie, i));
            if (v <= 0) continue;
            double varredura = v / total * Math.PI * 2;
            pagina.fatia(cx, cy, raio, angulo, angulo + varredura, PALETA[i % PALETA.length]);
            angulo += varredura;
        }
        if (rosca) pagina.circulo(cx, cy, raio * 0.56f, Color.WHITE);
        float lx = cx + raio + 36;
        float ly = cy + Math.min(raio, rotulos.size() * 7f);
        for (int i = 0; i < rotulos.size(); i++) {
            double v = Math.max(0, valor(serie, i));
            pagina.retangulo(lx, ly - 7, 8, 8, PALETA[i % PALETA.length]);
            String texto = pagina.fontes.cortar(rotulos.get(i), pagina.fontes.normal, 8, 190) + "  "
                    + formatar(v, formato, true) + " (" + formatar(v / total, "percentual", false) + ")";
            pagina.texto(texto, lx + 13, ly - 6, pagina.fontes.normal, 8, TEXTO);
            ly -= 14;
        }
    }

    @SuppressWarnings("unchecked")
    private void dispersao(Pagina pagina, Map<String, Object> grafico, float base, float altura) throws IOException {
        List<Map<String, Object>> pontos = (List<Map<String, Object>>) grafico.getOrDefault("pontos", List.of());
        if (pontos.isEmpty()) return;
        String formato = Objects.toString(grafico.get("formato"), "numero");
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = 0, maxY = 0;
        for (Map<String, Object> ponto : pontos) {
            double x = ((Number) ponto.get("x")).doubleValue();
            double y = ((Number) ponto.get("y")).doubleValue();
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        double[] escalaY = escala(minY, maxY);
        double[] escalaX = escalaLivre(minX, maxX);
        float x0 = MARGEM + 46;
        float x1 = MARGEM + LARGURA_UTIL;
        float y0 = base + 28;
        float y1 = base + altura - 6;
        eixoY(pagina, escalaY, x0, x1, y0, y1, formato);
        for (double tick = escalaX[0]; tick <= escalaX[1] + escalaX[2] / 2; tick += escalaX[2]) {
            float x = (float) (x0 + (tick - escalaX[0]) / (escalaX[1] - escalaX[0]) * (x1 - x0));
            String texto = formatar(tick, "numero", true);
            float w = pagina.fontes.largura(texto, pagina.fontes.normal, 7);
            pagina.texto(texto, x - w / 2, y0 - 11, pagina.fontes.normal, 7, TEXTO_SUAVE);
        }
        for (Map<String, Object> ponto : pontos) {
            double x = ((Number) ponto.get("x")).doubleValue();
            double y = ((Number) ponto.get("y")).doubleValue();
            float px = (float) (x0 + (x - escalaX[0]) / (escalaX[1] - escalaX[0]) * (x1 - x0));
            pagina.ponto(px, posicao(y, escalaY, y0, y1), 2.2f, MARCA);
        }
        String eixoX = Objects.toString(grafico.get("eixoX"), "");
        float w = pagina.fontes.largura(eixoX, pagina.fontes.normal, 7.5f);
        pagina.texto(eixoX, x0 + (x1 - x0) / 2 - w / 2, base + 4, pagina.fontes.normal, 7.5f, TEXTO_SUAVE);
    }

    private void eixoY(Pagina pagina, double[] escala, float x0, float x1, float y0, float y1, String formato) throws IOException {
        for (double tick = escala[0]; tick <= escala[1] + escala[2] / 2; tick += escala[2]) {
            float y = posicao(tick, escala, y0, y1);
            pagina.linha(x0, y, x1, y, BORDA, 0.5f);
            String texto = formatar(tick, formato, true);
            float w = pagina.fontes.largura(texto, pagina.fontes.normal, 7);
            pagina.texto(texto, x0 - 5 - w, y - 2.5f, pagina.fontes.normal, 7, TEXTO_SUAVE);
        }
    }

    private void rotulosX(Pagina pagina, List<String> rotulos, float x0, float faixa, float y0, boolean rotacionar)
            throws IOException {
        int passo = Math.max(1, (int) Math.ceil(rotulos.size() / (rotacionar ? 40.0 : 14.0)));
        for (int i = 0; i < rotulos.size(); i += passo) {
            float centro = x0 + i * faixa + faixa / 2;
            if (rotacionar) {
                String texto = pagina.fontes.cortar(rotulos.get(i), pagina.fontes.normal, 6.5f, 62);
                float w = pagina.fontes.largura(texto, pagina.fontes.normal, 6.5f);
                double cos = Math.cos(Math.toRadians(45));
                pagina.textoRotacionado(texto, (float) (centro - w * cos + 2), (float) (y0 - 6 - w * cos), 45,
                        pagina.fontes.normal, 6.5f, TEXTO_SUAVE);
            } else {
                String texto = pagina.fontes.cortar(rotulos.get(i), pagina.fontes.normal, 7, Math.max(20, faixa * passo - 2));
                float w = pagina.fontes.largura(texto, pagina.fontes.normal, 7);
                pagina.texto(texto, centro - w / 2, y0 - 11, pagina.fontes.normal, 7, TEXTO_SUAVE);
            }
        }
    }

    private void legenda(Pagina pagina, List<String> nomes, float y) throws IOException {
        float x = MARGEM + 46;
        for (int i = 0; i < nomes.size(); i++) {
            String nome = pagina.fontes.cortar(nomes.get(i), pagina.fontes.normal, 7.5f, 120);
            float w = pagina.fontes.largura(nome, pagina.fontes.normal, 7.5f);
            if (x + w + 20 > MARGEM + LARGURA_UTIL) break;
            pagina.retangulo(x, y + 1, 8, 8, PALETA[i % PALETA.length]);
            pagina.texto(nome, x + 11, y + 2, pagina.fontes.normal, 7.5f, TEXTO);
            x += w + 24;
        }
    }

    // ------------------------------------------------------------------------------------ tabelas

    @SuppressWarnings("unchecked")
    private void tabela(Pagina pagina, Map<String, Object> tabela) throws IOException {
        List<Map<String, Object>> colunas = (List<Map<String, Object>>) tabela.getOrDefault("colunas", List.of());
        List<List<Object>> linhas = (List<List<Object>>) tabela.getOrDefault("linhas", List.of());
        if (colunas.isEmpty()) return;
        pagina.garantir(60);
        String titulo = Objects.toString(tabela.get("titulo"), "");
        if (!titulo.isBlank()) {
            pagina.texto(pagina.fontes.cortar(titulo, pagina.fontes.negrito, 11, LARGURA_UTIL), MARGEM, pagina.y - 11,
                    pagina.fontes.negrito, 11, TEXTO);
            pagina.y -= 18;
        }
        float tamanho = colunas.size() > 8 ? 6.5f : 7.5f;
        float[] larguras = largurasColunas(pagina, colunas, linhas, tamanho);
        boolean[] numericas = new boolean[colunas.size()];
        for (int c = 0; c < colunas.size(); c++) {
            String tipo = Objects.toString(colunas.get(c).get("tipo"), "texto");
            numericas[c] = tipo.equals("inteiro") || tipo.equals("numero");
        }
        float alturaLinha = tamanho + 7;
        cabecalhoTabela(pagina, colunas, larguras, tamanho, alturaLinha);
        int indice = 0;
        for (List<Object> linha : linhas) {
            if (pagina.y - alturaLinha < BASE_CONTEUDO) {
                pagina.novaPagina();
                cabecalhoTabela(pagina, colunas, larguras, tamanho, alturaLinha);
            }
            if (indice % 2 == 1) pagina.retangulo(MARGEM, pagina.y - alturaLinha, LARGURA_UTIL, alturaLinha, ZEBRA);
            float x = MARGEM;
            for (int c = 0; c < colunas.size(); c++) {
                Object valor = c < linha.size() ? linha.get(c) : null;
                String nome = Objects.toString(colunas.get(c).get("nome"), "");
                String texto = celula(valor, Objects.toString(colunas.get(c).get("tipo"), "texto"), nome);
                texto = pagina.fontes.cortar(texto, pagina.fontes.normal, tamanho, larguras[c] - 8);
                float w = pagina.fontes.largura(texto, pagina.fontes.normal, tamanho);
                float tx = numericas[c] ? x + larguras[c] - 4 - w : x + 4;
                pagina.texto(texto, tx, pagina.y - alturaLinha + 4.5f, pagina.fontes.normal, tamanho, TEXTO);
                x += larguras[c];
            }
            pagina.y -= alturaLinha;
            indice++;
        }
        pagina.linha(MARGEM, pagina.y, MARGEM + LARGURA_UTIL, pagina.y, BORDA, 0.8f);
        pagina.y -= 4;
        Object total = tabela.get("total");
        String rodape = "Fonte: " + tabela.getOrDefault("resultado", "") + " (" + procedenciaLegivel(tabela.get("procedencia")) + ")"
                + (total instanceof Number n && n.intValue() > linhas.size()
                ? " - exibindo " + linhas.size() + " de " + formatar(n, "inteiro", false) + " linhas" : "");
        pagina.texto(pagina.fontes.cortar(rodape, pagina.fontes.normal, 7.5f, LARGURA_UTIL), MARGEM, pagina.y - 8,
                pagina.fontes.normal, 7.5f, TEXTO_SUAVE);
        pagina.y -= 22;
    }

    private void cabecalhoTabela(Pagina pagina, List<Map<String, Object>> colunas, float[] larguras, float tamanho,
            float alturaLinha) throws IOException {
        float altura = alturaLinha + 3;
        pagina.retangulo(MARGEM, pagina.y - altura, LARGURA_UTIL, altura, MARCA_ESCURA);
        float x = MARGEM;
        for (int c = 0; c < colunas.size(); c++) {
            String rotulo = pagina.fontes.cortar(Objects.toString(colunas.get(c).get("rotulo"), ""), pagina.fontes.negrito,
                    tamanho, larguras[c] - 8);
            pagina.texto(rotulo, x + 4, pagina.y - altura + 6, pagina.fontes.negrito, tamanho, Color.WHITE);
            x += larguras[c];
        }
        pagina.y -= altura;
    }

    private float[] largurasColunas(Pagina pagina, List<Map<String, Object>> colunas, List<List<Object>> linhas, float tamanho)
            throws IOException {
        float[] naturais = new float[colunas.size()];
        for (int c = 0; c < colunas.size(); c++) {
            naturais[c] = pagina.fontes.largura(Objects.toString(colunas.get(c).get("rotulo"), ""), pagina.fontes.negrito, tamanho) + 10;
        }
        int amostra = 0;
        for (List<Object> linha : linhas) {
            if (amostra++ > 60) break;
            for (int c = 0; c < colunas.size(); c++) {
                String texto = celula(c < linha.size() ? linha.get(c) : null, Objects.toString(colunas.get(c).get("tipo"), "texto"),
                        Objects.toString(colunas.get(c).get("nome"), ""));
                naturais[c] = Math.max(naturais[c], Math.min(180, pagina.fontes.largura(texto, pagina.fontes.normal, tamanho) + 10));
            }
        }
        float soma = 0;
        for (float largura : naturais) soma += largura;
        float[] finais = new float[colunas.size()];
        for (int c = 0; c < colunas.size(); c++) finais[c] = naturais[c] / soma * LARGURA_UTIL;
        return finais;
    }

    // ------------------------------------------------------------------------------------ procedencia e rodape

    @SuppressWarnings("unchecked")
    private void procedencia(Pagina pagina, List<Map<String, Object>> procedencia) throws IOException {
        pagina.garantir(90);
        pagina.subtitulo("Procedência dos dados");
        String nota = "Todos os números deste relatório foram calculados a partir dos resultados abaixo, obtidos pelo Copiloto "
                + "com as permissões do solicitante no momento da geração. INTERNO = dados do Nexo; ARQUIVO = anexo enviado; "
                + "CALCULO = processamento sobre os dados; EXTERNO = fonte pública da internet.";
        for (String linha : pagina.fontes.quebrar(nota, pagina.fontes.normal, 8, LARGURA_UTIL)) {
            pagina.garantir(12);
            pagina.texto(linha, MARGEM, pagina.y - 8, pagina.fontes.normal, 8, TEXTO_SUAVE);
            pagina.y -= 11.5f;
        }
        pagina.y -= 6;
        if (procedencia == null || procedencia.isEmpty()) {
            pagina.texto("Nenhum resultado de dados foi utilizado neste relatório.", MARGEM, pagina.y - 9, pagina.fontes.normal, 9, TEXTO);
            pagina.y -= 16;
            return;
        }
        for (Map<String, Object> item : procedencia) {
            String cabecalho = item.get("codigo") + " - " + item.get("titulo");
            String detalhes = procedenciaLegivel(item.get("natureza")) + " | " + formatar(item.get("linhas"), "inteiro", false)
                    + " linhas | origem: " + Objects.toString(item.get("origem"), "-");
            List<String> linhas = new ArrayList<>(pagina.fontes.quebrar(cabecalho, pagina.fontes.negrito, 8.5f, LARGURA_UTIL));
            pagina.garantir(14 * (linhas.size() + 2));
            for (String linha : linhas) {
                pagina.texto(linha, MARGEM, pagina.y - 8.5f, pagina.fontes.negrito, 8.5f, TEXTO);
                pagina.y -= 12;
            }
            pagina.texto(pagina.fontes.cortar(detalhes, pagina.fontes.normal, 7.5f, LARGURA_UTIL), MARGEM, pagina.y - 7.5f,
                    pagina.fontes.normal, 7.5f, TEXTO_SUAVE);
            pagina.y -= 11;
            if (item.get("consulta") instanceof Map<?, ?> consulta && !consulta.isEmpty()) {
                StringBuilder parametros = new StringBuilder("Parâmetros: ");
                ((Map<String, Object>) consulta).forEach((chave, valor) -> {
                    if (valor != null && !String.valueOf(valor).isBlank()) parametros.append(chave).append('=').append(valor).append("; ");
                });
                for (String linha : pagina.fontes.quebrar(parametros.toString(), pagina.fontes.normal, 7, LARGURA_UTIL)) {
                    pagina.garantir(10);
                    pagina.texto(linha, MARGEM, pagina.y - 7, pagina.fontes.normal, 7, TEXTO_SUAVE);
                    pagina.y -= 9.5f;
                }
            }
            pagina.y -= 6;
        }
    }

    private void rodapes(PDDocument document, Fontes fontes, Relatorio relatorio) throws IOException {
        int total = document.getNumberOfPages();
        String geradoEm = DATA_HORA.format(relatorio.geradoEm().atZoneSameInstant(ZONA));
        for (int i = 0; i < total; i++) {
            PDPage page = document.getPage(i);
            try (PDPageContentStream cs = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                cs.setNonStrokingColor(MARCA);
                cs.addRect(0, ALTURA_PAGINA - 6, LARGURA_PAGINA, 6);
                cs.fill();
                escrever(cs, fontes, "Nexo  |  Copiloto", MARGEM, ALTURA_PAGINA - 30, fontes.negrito, 8, MARCA_ESCURA);
                String titulo = fontes.cortar(relatorio.titulo(), fontes.normal, 8, LARGURA_UTIL * 0.6f);
                escrever(cs, fontes, titulo, MARGEM + LARGURA_UTIL - fontes.largura(titulo, fontes.normal, 8), ALTURA_PAGINA - 30,
                        fontes.normal, 8, TEXTO_SUAVE);
                cs.setStrokingColor(BORDA);
                cs.setLineWidth(0.6f);
                cs.moveTo(MARGEM, 40);
                cs.lineTo(MARGEM + LARGURA_UTIL, 40);
                cs.stroke();
                escrever(cs, fontes, "Gerado pelo Copiloto (assistente do Nexo) em " + geradoEm
                        + ". Confira decisões críticas nos módulos de origem.", MARGEM, 28, fontes.normal, 7, TEXTO_SUAVE);
                String paginaTexto = "Página " + (i + 1) + " de " + total;
                escrever(cs, fontes, paginaTexto, MARGEM + LARGURA_UTIL - fontes.largura(paginaTexto, fontes.normal, 7.5f), 28,
                        fontes.normal, 7.5f, TEXTO_SUAVE);
            }
        }
    }

    private static void escrever(PDPageContentStream cs, Fontes fontes, String texto, float x, float y, PDType1Font fonte,
            float tamanho, Color cor) throws IOException {
        cs.beginText();
        cs.setFont(fonte, tamanho);
        cs.setNonStrokingColor(cor);
        cs.newLineAtOffset(x, y);
        cs.showText(fontes.seguro(texto));
        cs.endText();
    }

    // ------------------------------------------------------------------------------------ utilitarios numericos

    private static double valor(Map<String, Object> serie, int indice) {
        Object valores = serie.get("valores");
        if (!(valores instanceof List<?> lista) || indice >= lista.size()) return 0;
        return lista.get(indice) instanceof Number numero ? numero.doubleValue() : 0;
    }

    private static float posicao(double valor, double[] escala, float y0, float y1) {
        return (float) (y0 + (valor - escala[0]) / (escala[1] - escala[0]) * (y1 - y0));
    }

    static double[] escala(double minimo, double maximo) {
        double min = Math.min(0, minimo);
        double max = Math.max(0, maximo);
        if (max == min) max = min + 1;
        double passo = numeroRedondo((max - min) / 4, true);
        return new double[]{Math.floor(min / passo) * passo, Math.ceil(max / passo) * passo, passo};
    }

    private static double[] escalaLivre(double minimo, double maximo) {
        if (maximo == minimo) {
            minimo -= 1;
            maximo += 1;
        }
        double passo = numeroRedondo((maximo - minimo) / 5, true);
        return new double[]{Math.floor(minimo / passo) * passo, Math.ceil(maximo / passo) * passo, passo};
    }

    private static double numeroRedondo(double faixa, boolean arredondar) {
        double expoente = Math.floor(Math.log10(faixa));
        double fracao = faixa / Math.pow(10, expoente);
        double redondo;
        if (arredondar) redondo = fracao < 1.5 ? 1 : fracao < 3 ? 2 : fracao < 7 ? 5 : 10;
        else redondo = fracao <= 1 ? 1 : fracao <= 2 ? 2 : fracao <= 5 ? 5 : 10;
        return redondo * Math.pow(10, expoente);
    }

    static String formatar(Object valor, String formato, boolean compacto) {
        if (!(valor instanceof Number numero)) return valor == null ? "-" : valor.toString();
        double v = numero.doubleValue();
        DecimalFormatSymbols simbolos = DecimalFormatSymbols.getInstance(PT_BR);
        return switch (formato == null ? "numero" : formato) {
            case "moeda" -> compacto && Math.abs(v) >= 10_000 ? "R$ " + compacto(v, simbolos)
                    : "R$ " + new DecimalFormat("#,##0.00", simbolos).format(v);
            case "percentual" -> new DecimalFormat("#,##0.0", simbolos).format(v * 100) + "%";
            case "inteiro" -> compacto && Math.abs(v) >= 100_000 ? compacto(v, simbolos) : new DecimalFormat("#,##0", simbolos).format(v);
            default -> compacto && Math.abs(v) >= 100_000 ? compacto(v, simbolos)
                    : new DecimalFormat(Math.abs(v) >= 100 ? "#,##0" : "#,##0.##", simbolos).format(v);
        };
    }

    private static String compacto(double v, DecimalFormatSymbols simbolos) {
        DecimalFormat formato = new DecimalFormat("#,##0.#", simbolos);
        double abs = Math.abs(v);
        if (abs >= 1e9) return formato.format(v / 1e9) + " bi";
        if (abs >= 1e6) return formato.format(v / 1e6) + " mi";
        if (abs >= 1e3) return formato.format(v / 1e3) + " mil";
        return formato.format(v);
    }

    private static String celula(Object valor, String tipo, String coluna) {
        if (valor == null) return "";
        if (valor instanceof Number numero) {
            String nome = coluna.toLowerCase(PT_BR);
            if (nome.contains("valor") || nome.contains("custo") || nome.contains("preco")) return formatar(numero, "moeda", false);
            return formatar(numero, "inteiro".equals(tipo) ? "inteiro" : "numero", false);
        }
        if (valor instanceof Boolean bool) return bool ? "Sim" : "Não";
        String texto = valor.toString();
        if ("data".equals(tipo) && texto.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
            return texto.substring(8, 10) + "/" + texto.substring(5, 7) + "/" + texto.substring(0, 4);
        }
        if ("data_hora".equals(tipo) && texto.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}.*")) {
            return texto.substring(8, 10) + "/" + texto.substring(5, 7) + "/" + texto.substring(0, 4) + " " + texto.substring(11, 16);
        }
        return texto;
    }

    private static String procedenciaLegivel(Object natureza) {
        return switch (Objects.toString(natureza, "")) {
            case "INTERNO" -> "dados internos do Nexo";
            case "ARQUIVO" -> "arquivo anexado";
            case "CALCULO" -> "cálculo sobre dados";
            case "EXTERNO" -> "fonte externa";
            default -> Objects.toString(natureza, "");
        };
    }

    private static Color clarear(Color cor, float fator) {
        return new Color(Math.round(cor.getRed() + (255 - cor.getRed()) * fator),
                Math.round(cor.getGreen() + (255 - cor.getGreen()) * fator),
                Math.round(cor.getBlue() + (255 - cor.getBlue()) * fator));
    }

    private static boolean naoVazio(String texto) {
        return texto != null && !texto.isBlank();
    }

    // ------------------------------------------------------------------------------------ pagina

    record Linha(String texto, float tamanho, boolean negrito, float recuo, String marcador, float altura) {
    }

    /** Fontes padrao com saneamento para WinAnsi, medicao e quebra de linhas. */
    static final class Fontes {
        final PDType1Font normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        final PDType1Font negrito = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private final Map<Integer, Boolean> suportados = new HashMap<>();
        private static final Map<Integer, String> SUBSTITUTOS = Map.ofEntries(
                Map.entry((int) '\u2192', "->"), Map.entry((int) '\u2190', "<-"), Map.entry((int) '\u2191', "^"),
                Map.entry((int) '\u2193', "v"), Map.entry((int) '\u2265', ">="), Map.entry((int) '\u2264', "<="),
                Map.entry((int) '\u2248', "~"), Map.entry((int) '\u2260', "!="), Map.entry((int) '\u0394', "Var. "),
                Map.entry((int) '\u2713', "OK"), Map.entry((int) '\u2714', "OK"), Map.entry((int) '\u2717', "X"),
                Map.entry((int) '\u25b2', "+"), Map.entry((int) '\u25bc', "-"), Map.entry((int) '\u2212', "-"),
                Map.entry((int) '\u2011', "-"), Map.entry((int) '\u00a0', " "), Map.entry((int) '\u202f', " "),
                Map.entry((int) '\u2009', " "), Map.entry((int) '\u200b', ""), Map.entry((int) '\t', "    "));

        String seguro(String texto) {
            if (texto == null) return "";
            StringBuilder resultado = new StringBuilder(texto.length());
            texto.codePoints().forEach(codigo -> {
                String substituto = SUBSTITUTOS.get(codigo);
                if (substituto != null) {
                    resultado.append(substituto);
                    return;
                }
                if (Character.isISOControl(codigo)) return;
                if (suportado(codigo)) resultado.appendCodePoint(codigo);
                else if (Character.getType(codigo) == Character.OTHER_SYMBOL || Character.isSurrogate((char) codigo)) {
                    // emojis e simbolos sem glifo sao omitidos
                } else {
                    String decomposto = java.text.Normalizer.normalize(new String(Character.toChars(codigo)), java.text.Normalizer.Form.NFD)
                            .replaceAll("\\p{M}", "");
                    if (!decomposto.isEmpty() && decomposto.codePoints().allMatch(this::suportado)) resultado.append(decomposto);
                    else resultado.append('?');
                }
            });
            return resultado.toString();
        }

        private boolean suportado(int codigo) {
            return suportados.computeIfAbsent(codigo, ignored -> {
                try {
                    normal.encode(new String(Character.toChars(codigo)));
                    return true;
                } catch (IOException | IllegalArgumentException exception) {
                    return false;
                }
            });
        }

        float largura(String texto, PDType1Font fonte, float tamanho) throws IOException {
            return fonte.getStringWidth(seguro(texto)) / 1000f * tamanho;
        }

        String cortar(String texto, PDType1Font fonte, float tamanho, float maximo) throws IOException {
            String limpo = seguro(texto);
            if (largura(limpo, fonte, tamanho) <= maximo) return limpo;
            String reticencias = "...";
            int fim = limpo.length();
            while (fim > 0 && largura(limpo.substring(0, fim) + reticencias, fonte, tamanho) > maximo) fim--;
            return fim <= 0 ? "" : limpo.substring(0, fim).stripTrailing() + reticencias;
        }

        List<String> quebrar(String texto, PDType1Font fonte, float tamanho, float maximo) throws IOException {
            List<String> linhas = new ArrayList<>();
            for (String paragrafo : seguro(texto).split("\n")) {
                StringBuilder atual = new StringBuilder();
                for (String palavra : paragrafo.split(" +")) {
                    if (palavra.isEmpty()) continue;
                    String candidato = atual.isEmpty() ? palavra : atual + " " + palavra;
                    if (largura(candidato, fonte, tamanho) <= maximo) {
                        atual.setLength(0);
                        atual.append(candidato);
                        continue;
                    }
                    if (!atual.isEmpty()) linhas.add(atual.toString());
                    atual.setLength(0);
                    String resto = palavra;
                    while (largura(resto, fonte, tamanho) > maximo && resto.length() > 1) {
                        int corte = resto.length() - 1;
                        while (corte > 1 && largura(resto.substring(0, corte), fonte, tamanho) > maximo) corte--;
                        linhas.add(resto.substring(0, corte));
                        resto = resto.substring(corte);
                    }
                    atual.append(resto);
                }
                if (!atual.isEmpty()) linhas.add(atual.toString());
            }
            return linhas;
        }

        /** Markdown simplificado: titulos, listas, negrito de linha inteira, links e paragrafos. */
        List<Linha> markdown(String texto, float tamanho, float largura) throws IOException {
            List<Linha> linhas = new ArrayList<>();
            boolean anteriorVazia = true;
            for (String bruta : Objects.toString(texto, "").replace("\r", "").split("\n")) {
                String linha = bruta.strip();
                if (linha.isEmpty() || linha.matches("^[-*_]{3,}$")) {
                    if (!anteriorVazia) linhas.add(new Linha("", tamanho, false, 0, null, tamanho * 0.6f));
                    anteriorVazia = true;
                    continue;
                }
                anteriorVazia = false;
                if (linha.startsWith("|")) linha = linha.replaceAll("^\\|\\s*|\\s*\\|$", "").replaceAll("\\s*\\|\\s*", "  |  ");
                if (linha.matches("^[|\\-\\s:]+$")) continue;
                boolean titulo = linha.startsWith("#");
                boolean negrito = titulo || (linha.startsWith("**") && linha.endsWith("**") && linha.length() > 4);
                float tamanhoLinha = titulo ? tamanho + (linha.startsWith("# ") ? 3 : linha.startsWith("## ") ? 2 : 1) : tamanho;
                String marcador = null;
                float recuo = 0;
                if (linha.matches("^[-*+] .*")) {
                    marcador = "\u2022";
                    recuo = 12;
                    linha = linha.substring(2);
                } else if (linha.matches("^\\d{1,3}[.)] .*")) {
                    int espaco = linha.indexOf(' ');
                    marcador = linha.substring(0, espaco);
                    recuo = 16;
                    linha = linha.substring(espaco + 1);
                }
                linha = linha.replaceAll("^#+\\s*", "")
                        .replaceAll("\\[([^\\]]+)]\\((https?://[^)]+)\\)", "$1 ($2)")
                        .replace("**", "").replace("__", "").replace("`", "");
                List<String> quebradas = quebrar(linha, negrito ? negrito() : normal, tamanhoLinha, largura - recuo);
                for (int i = 0; i < quebradas.size(); i++) {
                    linhas.add(new Linha(quebradas.get(i), tamanhoLinha, negrito, recuo, i == 0 ? marcador : null,
                            tamanhoLinha * 1.45f));
                }
                if (titulo) linhas.add(new Linha("", tamanho, false, 0, null, 3));
            }
            return linhas;
        }

        private PDType1Font negrito() {
            return negrito;
        }
    }

    /** Pagina corrente com cursor vertical e primitivas de desenho. */
    static final class Pagina {
        final PDDocument document;
        final Fontes fontes;
        final String tituloRelatorio;
        PDPageContentStream cs;
        float y;

        Pagina(PDDocument document, Fontes fontes, String tituloRelatorio) throws IOException {
            this.document = document;
            this.fontes = fontes;
            this.tituloRelatorio = tituloRelatorio;
            novaPagina();
        }

        void novaPagina() throws IOException {
            if (cs != null) cs.close();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            cs = new PDPageContentStream(document, page);
            y = TOPO_CONTEUDO;
        }

        void garantir(float altura) throws IOException {
            if (y - altura < BASE_CONTEUDO) novaPagina();
        }

        void fechar() throws IOException {
            if (cs != null) cs.close();
            cs = null;
        }

        void subtitulo(String texto) throws IOException {
            garantir(40);
            y -= 6;
            texto(fontes.cortar(texto, fontes.negrito, 13.5f, LARGURA_UTIL), MARGEM, y - 13.5f, fontes.negrito, 13.5f, MARCA_ESCURA);
            y -= 20;
            linha(MARGEM, y, MARGEM + 36, y, MARCA, 1.4f);
            y -= 12;
        }

        void texto(String texto, float x, float yTexto, PDType1Font fonte, float tamanho, Color cor) throws IOException {
            String limpo = fontes.seguro(texto);
            if (limpo.isEmpty()) return;
            cs.beginText();
            cs.setFont(fonte, tamanho);
            cs.setNonStrokingColor(cor);
            cs.newLineAtOffset(x, yTexto);
            cs.showText(limpo);
            cs.endText();
        }

        void textoRotacionado(String texto, float x, float yTexto, double graus, PDType1Font fonte, float tamanho, Color cor)
                throws IOException {
            String limpo = fontes.seguro(texto);
            if (limpo.isEmpty()) return;
            cs.beginText();
            cs.setFont(fonte, tamanho);
            cs.setNonStrokingColor(cor);
            cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(graus), x, yTexto));
            cs.showText(limpo);
            cs.endText();
        }

        void retangulo(float x, float yBase, float largura, float altura, Color cor) throws IOException {
            if (largura <= 0 || altura <= 0) return;
            cs.setNonStrokingColor(cor);
            cs.addRect(x, yBase, largura, altura);
            cs.fill();
        }

        void contorno(float x, float yBase, float largura, float altura, Color cor) throws IOException {
            cs.setStrokingColor(cor);
            cs.setLineWidth(0.8f);
            cs.addRect(x, yBase, largura, altura);
            cs.stroke();
        }

        void linha(float xa, float ya, float xb, float yb, Color cor, float espessura) throws IOException {
            cs.setStrokingColor(cor);
            cs.setLineWidth(espessura);
            cs.moveTo(xa, ya);
            cs.lineTo(xb, yb);
            cs.stroke();
        }

        void polilinha(List<float[]> pontos, Color cor, float espessura) throws IOException {
            if (pontos.size() < 2) return;
            cs.setStrokingColor(cor);
            cs.setLineWidth(espessura);
            cs.moveTo(pontos.getFirst()[0], pontos.getFirst()[1]);
            for (int i = 1; i < pontos.size(); i++) cs.lineTo(pontos.get(i)[0], pontos.get(i)[1]);
            cs.stroke();
        }

        void poligonoArea(List<float[]> pontos, float zero, Color cor) throws IOException {
            if (pontos.size() < 2) return;
            cs.setNonStrokingColor(cor);
            cs.moveTo(pontos.getFirst()[0], zero);
            for (float[] ponto : pontos) cs.lineTo(ponto[0], ponto[1]);
            cs.lineTo(pontos.getLast()[0], zero);
            cs.closePath();
            cs.fill();
        }

        void ponto(float x, float yPonto, float raio, Color cor) throws IOException {
            circulo(x, yPonto, raio, cor);
        }

        void circulo(float cx, float cy, float raio, Color cor) throws IOException {
            float k = 0.5523f * raio;
            cs.setNonStrokingColor(cor);
            cs.moveTo(cx + raio, cy);
            cs.curveTo(cx + raio, cy + k, cx + k, cy + raio, cx, cy + raio);
            cs.curveTo(cx - k, cy + raio, cx - raio, cy + k, cx - raio, cy);
            cs.curveTo(cx - raio, cy - k, cx - k, cy - raio, cx, cy - raio);
            cs.curveTo(cx + k, cy - raio, cx + raio, cy - k, cx + raio, cy);
            cs.closePath();
            cs.fill();
        }

        /** Fatia de pizza no sentido horario a partir do topo; arcos aproximados por curvas de Bezier. */
        void fatia(float cx, float cy, float raio, double inicio, double fim, Color cor) throws IOException {
            cs.setNonStrokingColor(cor);
            cs.moveTo(cx, cy);
            double a0 = Math.PI / 2 - inicio;
            cs.lineTo((float) (cx + raio * Math.cos(a0)), (float) (cy + raio * Math.sin(a0)));
            double restante = fim - inicio;
            double atual = inicio;
            while (restante > 1e-6) {
                double passo = Math.min(Math.PI / 2, restante);
                double alfa = Math.PI / 2 - atual;
                double beta = Math.PI / 2 - (atual + passo);
                double k = 4.0 / 3.0 * Math.tan((beta - alfa) / 4);
                cs.curveTo(
                        (float) (cx + raio * (Math.cos(alfa) - k * Math.sin(alfa))),
                        (float) (cy + raio * (Math.sin(alfa) + k * Math.cos(alfa))),
                        (float) (cx + raio * (Math.cos(beta) + k * Math.sin(beta))),
                        (float) (cy + raio * (Math.sin(beta) - k * Math.cos(beta))),
                        (float) (cx + raio * Math.cos(beta)),
                        (float) (cy + raio * Math.sin(beta)));
                atual += passo;
                restante -= passo;
            }
            cs.closePath();
            cs.fill();
            if (fim - inicio < Math.PI * 2 - 1e-6) {
                linha(cx, cy, (float) (cx + raio * Math.cos(a0)), (float) (cy + raio * Math.sin(a0)), Color.WHITE, 1.2f);
            }
        }
    }
}
