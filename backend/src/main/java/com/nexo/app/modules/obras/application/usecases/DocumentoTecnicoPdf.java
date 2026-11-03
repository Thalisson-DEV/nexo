package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.domain.EquipamentoObra;
import com.nexo.app.modules.obras.domain.FichaAsBuilt;
import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.obras.domain.OperacaoItemKit;
import com.nexo.app.modules.obras.domain.PontoPlanejamento;
import com.nexo.app.modules.obras.domain.RespostasChecklist;
import com.nexo.app.modules.obras.domain.VaoPlanejamento;
import com.nexo.app.modules.obras.domain.VisitaPreviaObra;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * PDFs da documentação técnica. As coordenadas de ponto e vão são relativas à página sem rotação (D3), a partir do
 * canto superior esquerdo. Usa as fontes padrão do PDF; caractere fora do conjunto delas vira "?".
 */
@Component
public class DocumentoTecnicoPdf {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color EXECUTADO = new Color(0x15, 0x80, 0x3d);
    private static final Color FUNDO_ROTULO = Color.WHITE;
    private static final Locale PT_BR = Locale.of("pt", "BR");

    public record DadosAsBuilt(Obra obra, int numeroVersao, byte[] projeto, List<PontoPlanejamento> pontos, Set<UUID> pontosExecutados,
                               List<VaoPlanejamento> vaos, Set<UUID> vaosExecutados, List<FichaAsBuilt> fichas, List<EquipamentoObra> equipamentos,
                               Map<UUID, List<Foto>> fotos) {
    }

    /** Foto do book, já em JPEG no tamanho do documento. */
    public record Foto(String item, String legenda, String autoria, byte[] jpeg) {
    }

    public byte[] visita(Obra obra, VisitaPreviaObra visita) {
        try (PDDocument documento = new PDDocument()) {
            Escrita escrita = new Escrita(documento, "Visita prévia · Obra " + obra.getNumero());
            escrita.titulo("Visita prévia da obra " + obra.getNumero());
            escrita.paragrafo(obra.getTitulo());
            escrita.campo("Modelo", visita.getTituloModelo());
            escrita.campo("Data da visita", visita.getDataVisita().format(DATA));
            escrita.campo("Responsável", visita.getResponsavel());
            respostas(escrita, visita.getRespostas());
            if (visita.getObservacoes() != null) {
                escrita.secao("Observações");
                escrita.paragrafo(visita.getObservacoes());
            }
            escrita.fechar();
            return salvar(documento);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gerar o PDF da visita prévia", e);
        }
    }

    public byte[] asBuilt(DadosAsBuilt dados) {
        try (PDDocument documento = Loader.loadPDF(dados.projeto())) {
            marcarFolhas(documento, dados);
            Map<UUID, PontoPlanejamento> pontos = dados.pontos().stream().collect(Collectors.toMap(PontoPlanejamento::getId, Function.identity()));
            Escrita escrita = new Escrita(documento, "As Built v" + dados.numeroVersao() + " · Obra " + dados.obra().getNumero());
            escrita.titulo("Book da obra " + dados.obra().getNumero());
            escrita.paragrafo(dados.obra().getTitulo());
            escrita.campo("Versão", String.valueOf(dados.numeroVersao()));
            dados.fichas().stream()
                    .filter(f -> pontos.containsKey(f.getIdPonto()))
                    .sorted(Comparator.comparing((FichaAsBuilt f) -> pontos.get(f.getIdPonto()).getPagina()).thenComparing(f -> pontos.get(f.getIdPonto()).getNumero()))
                    .forEach(f -> ficha(escrita, pontos.get(f.getIdPonto()), f, dados.fotos().getOrDefault(f.getIdPonto(), List.of())));
            equipamentos(escrita, dados.equipamentos(), pontos);
            escrita.fechar();
            return salvar(documento);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gerar o PDF do As Built", e);
        }
    }

    /** Relatório de medição detalhado: resumo, comparativos, faturado a conferir, medições, fotos por ponto e assinaturas. */
    byte[] relatorioMedicao(RelatorioMedicaoDetalhado r, Map<String, List<Foto>> fotos) {
        try (PDDocument documento = new PDDocument()) {
            Escrita escrita = new Escrita(documento, "Medição detalhada · Obra " + r.obra().getNumero());
            escrita.titulo("Relatório de medição detalhado");
            escrita.paragrafo("Obra " + r.obra().getNumero() + " · " + r.obra().getTitulo());
            if (r.municipio() != null) {
                escrita.campo("Município", r.municipio());
            }
            escrita.campo("Data de corte", r.dataCorte().format(DATA));
            if (r.fiscal() != null) {
                escrita.campo("Fiscal", r.fiscal());
            }
            if (r.responsavel() != null) {
                escrita.campo("Responsável", r.responsavel());
            }

            escrita.secao("Resumo");
            escrita.tabela(new String[]{"Indicador", "Valor (R$)"}, new float[]{380, 119}, new boolean[]{false, true}, List.of(
                    new String[]{"Orçado de mão de obra", dinheiro(r.orcadoMaoDeObra())},
                    new String[]{"Orçado de materiais", dinheiro(r.orcadoMateriais())},
                    new String[]{"Executado até a data de corte", dinheiro(r.executado())},
                    new String[]{"Faturado até a data de corte", dinheiro(r.faturadoTotal())},
                    new String[]{"Executado a faturar", dinheiro(r.aFaturar())},
                    new String[]{"Saldo do orçamento de mão de obra", dinheiro(r.saldoOrcado())},
                    new String[]{"Medido no sistema", dinheiro(r.medidoNoSistema())}), null);

            escrita.secao("Mão de obra");
            if (r.maoDeObra().isEmpty()) {
                escrita.paragrafo("Nenhum serviço orçado, planejado ou faturado.");
            } else {
                escrita.tabela(new String[]{"Código", "Serviço", "Op.", "Orçado", "Orçado R$", "Exec.", "Exec. R$", "Faturado", "Fat. R$", "A faturar R$"},
                        new float[]{44, 120, 26, 36, 50, 38, 50, 38, 50, 47},
                        new boolean[]{false, false, false, true, true, true, true, true, true, true},
                        r.maoDeObra().stream().map(l -> new String[]{l.codigo(), marcar(l), operacaoCurta(l.operacao()),
                                quantidade(l.orcadoQuantidade()), dinheiro(l.orcadoValor()), quantidade(l.executadoQuantidade()),
                                dinheiro(l.executadoValor()), quantidade(l.faturadoQuantidade()), dinheiro(l.faturadoValor()), dinheiro(l.aFaturar())}).toList(),
                        new String[]{"Total", "", "", "", dinheiro(r.orcadoMaoDeObra()), "", dinheiro(r.executado()), "", dinheiro(r.faturadoTotal()),
                                dinheiro(r.aFaturar())});
                if (r.maoDeObra().stream().anyMatch(RelatorioMedicaoDetalhado.Linha::acimaDoOrcado)) {
                    escrita.nota("* Executado acima do orçado ou fora do orçamento.");
                }
            }

            escrita.secao("Materiais");
            if (r.materiais().isEmpty()) {
                escrita.paragrafo("Nenhum material orçado ou planejado.");
            } else {
                escrita.tabela(new String[]{"Código", "Material", "Op.", "Un.", "Orçado", "Orçado R$", "Planejado", "Aplicado"},
                        new float[]{50, 190, 30, 30, 45, 60, 47, 47},
                        new boolean[]{false, false, false, false, true, true, true, true},
                        r.materiais().stream().map(l -> new String[]{l.codigo(), marcar(l), operacaoCurta(l.operacao()), texto(l.unidade()),
                                quantidade(l.orcadoQuantidade()), dinheiro(l.orcadoValor()), quantidade(l.planejadoQuantidade()),
                                quantidade(l.executadoQuantidade())}).toList(),
                        new String[]{"Total", "", "", "", "", dinheiro(r.orcadoMateriais()), "", ""});
            }

            if (!r.paraConferir().isEmpty()) {
                escrita.secao("Faturado para conferir");
                escrita.paragrafo("Lançamentos não aprovados ficam fora do faturado; créditos entram subtraindo.");
                escrita.tabela(new String[]{"Data", "Código", "Descrição", "Qtd.", "Valor R$", "Situação", "D/C"},
                        new float[]{50, 50, 194, 45, 70, 50, 40},
                        new boolean[]{false, false, false, true, true, false, false},
                        r.paraConferir().stream().map(l -> new String[]{l.data().format(DATA), l.codigo(), texto(l.descricao()),
                                quantidade(l.quantidade()), dinheiro(l.valorTotal()), l.situacao(), l.credito() ? "C" : "D"}).toList(), null);
            }
            if (!r.medicoes().isEmpty()) {
                escrita.secao("Medições no sistema");
                escrita.tabela(new String[]{"Medição", "Tipo", "Competência", "Situação", "Valor R$", "Gerada em"},
                        new float[]{60, 70, 70, 80, 99, 120},
                        new boolean[]{false, false, false, false, true, false},
                        r.medicoes().stream().map(m -> new String[]{"#" + m.numero(), m.tipo() != null && m.tipo().name().equals("CICLO") ? "Ciclo" : "Parcial",
                                m.competencia() != null ? m.competencia().format(DateTimeFormatter.ofPattern("MM/yyyy")) : "",
                                m.status().name().equals("GERADA") ? "Gerada" : "Estornada", dinheiro(m.valorTotal()),
                                m.dataCriacao() != null ? m.dataCriacao().atZoneSameInstant(ApontamentoExecucaoUseCases.FUSO).format(DATA_HORA) : ""}).toList(), null);
            }

            if (!fotos.isEmpty()) {
                // Título e primeira linha de fotos ficam na mesma página.
                float primeiraLinha = Escrita.ALTURA_FOTO + Escrita.ALTURA_LEGENDA;
                escrita.garantir(primeiraLinha + 90);
                escrita.secao("Fotos do book");
                for (Map.Entry<String, List<Foto>> ponto : fotos.entrySet()) {
                    escrita.garantir(primeiraLinha + 40);
                    escrita.grupo("Ponto " + ponto.getKey());
                    escrita.fotos(ponto.getValue());
                }
            }

            escrita.assinaturas(r.fiscal() != null ? r.fiscal() : "Fiscal", r.responsavel() != null ? r.responsavel() : "Responsável");
            escrita.fechar();
            return salvar(documento);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gerar o PDF do relatório de medição", e);
        }
    }

    private static String marcar(RelatorioMedicaoDetalhado.Linha linha) {
        String descricao = texto(linha.descricao());
        return linha.acimaDoOrcado() ? "* " + descricao : descricao;
    }

    private static String operacaoCurta(OperacaoItemKit operacao) {
        return operacao == null ? "" : operacao == OperacaoItemKit.INSTALAR ? "Inst." : "Ret.";
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor;
    }

    private static String dinheiro(BigDecimal valor) {
        if (valor == null) {
            return "";
        }
        DecimalFormat formato = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(PT_BR));
        return formato.format(valor);
    }

    private static String quantidade(BigDecimal valor) {
        if (valor == null) {
            return "";
        }
        DecimalFormat formato = new DecimalFormat("#,##0.####", DecimalFormatSymbols.getInstance(PT_BR));
        return formato.format(valor);
    }

    private static void ficha(Escrita escrita, PontoPlanejamento ponto, FichaAsBuilt ficha, List<Foto> fotos) {
        try {
            escrita.secao("Ponto " + ponto.getNumero() + " · folha " + ponto.getPagina());
            respostas(escrita, ficha.getRespostas());
            escrita.grupo("Evidências");
            if (fotos.isEmpty()) {
                escrita.paragrafo("Nenhuma foto lançada neste ponto.");
            }
            escrita.fotos(fotos);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void equipamentos(Escrita escrita, List<EquipamentoObra> equipamentos, Map<UUID, PontoPlanejamento> pontos) throws IOException {
        List<EquipamentoObra> daObra = equipamentos.stream().filter(e -> pontos.containsKey(e.getIdPonto())).toList();
        if (daObra.isEmpty()) {
            return;
        }
        escrita.secao("Equipamentos");
        for (EquipamentoObra e : daObra) {
            String tipo = e.getTipo() == EquipamentoObra.Tipo.RELIGADOR
                    ? (e.isTrafoAuxiliar() ? "Religador com trafo auxiliar" : "Religador sem trafo auxiliar") : "Regulador";
            escrita.campo("Ponto " + pontos.get(e.getIdPonto()).getNumero(), e.getObservacao() != null ? tipo + " · " + e.getObservacao() : tipo);
        }
    }

    private static void respostas(Escrita escrita, RespostasChecklist respostas) throws IOException {
        String grupo = null;
        for (RespostasChecklist.Item item : respostas.itens()) {
            if (!item.grupo().equals(grupo)) {
                grupo = item.grupo();
                escrita.grupo(grupo);
            }
            escrita.campo(item.enunciado(), resposta(item));
        }
    }

    private static String resposta(RespostasChecklist.Item item) {
        if (item.tipo() == com.nexo.app.modules.obras.domain.EstruturaChecklist.TipoPergunta.DATA && item.valor() != null && !item.naoSeAplica()) {
            return LocalDate.parse(item.valor()).format(DATA);
        }
        return item.resposta();
    }

    /** Fica só com as folhas que têm algo executado, e desenha nelas pontos e vãos. */
    private static void marcarFolhas(PDDocument documento, DadosAsBuilt dados) throws IOException {
        Map<UUID, PontoPlanejamento> pontos = dados.pontos().stream().collect(Collectors.toMap(PontoPlanejamento::getId, Function.identity()));
        List<VaoPlanejamento> vaos = dados.vaos().stream()
                .filter(v -> dados.vaosExecutados().contains(v.getId()) && pontos.containsKey(v.getIdPontoA()) && pontos.containsKey(v.getIdPontoB()))
                .toList();
        Set<Integer> folhas = new HashSet<>();
        dados.pontos().stream().filter(p -> dados.pontosExecutados().contains(p.getId())).forEach(p -> folhas.add(p.getPagina()));
        vaos.forEach(v -> {
            folhas.add(pontos.get(v.getIdPontoA()).getPagina());
            folhas.add(pontos.get(v.getIdPontoB()).getPagina());
        });
        int total = documento.getNumberOfPages();
        List<PDPage> paginas = new ArrayList<>();
        documento.getPages().forEach(paginas::add);
        PDType1Font fonte = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        for (int numero = 1; numero <= total; numero++) {
            if (folhas.contains(numero)) {
                marcarFolha(documento, paginas.get(numero - 1), numero, dados, vaos, pontos, fonte);
            } else {
                documento.removePage(paginas.get(numero - 1));
            }
        }
    }

    private static void marcarFolha(PDDocument documento, PDPage pagina, int numero, DadosAsBuilt dados, List<VaoPlanejamento> vaos,
                                    Map<UUID, PontoPlanejamento> pontos, PDType1Font fonte) throws IOException {
        PDRectangle caixa = pagina.getCropBox();
        float unidade = Math.max(caixa.getWidth(), caixa.getHeight()) / 220f;
        try (PDPageContentStream cs = new PDPageContentStream(documento, pagina, PDPageContentStream.AppendMode.APPEND, true, true)) {
            cs.setStrokingColor(EXECUTADO);
            cs.setLineWidth(unidade * 0.5f);
            for (VaoPlanejamento vao : vaos) {
                PontoPlanejamento a = pontos.get(vao.getIdPontoA());
                PontoPlanejamento b = pontos.get(vao.getIdPontoB());
                if (a.getPagina() == numero && b.getPagina() == numero) {
                    cs.moveTo(x(caixa, a), y(caixa, a));
                    cs.lineTo(x(caixa, b), y(caixa, b));
                    cs.stroke();
                } else if (a.getPagina() == numero || b.getPagina() == numero) {
                    PontoPlanejamento aqui = a.getPagina() == numero ? a : b;
                    PontoPlanejamento la = aqui == a ? b : a;
                    rotulo(cs, pagina, fonte, unidade, x(caixa, aqui), y(caixa, aqui) - unidade * 3.5f, "Vão até " + la.getNumero() + " (folha " + la.getPagina() + ")");
                }
            }
            for (PontoPlanejamento ponto : dados.pontos()) {
                if (ponto.getPagina() == numero && dados.pontosExecutados().contains(ponto.getId())) {
                    circulo(cs, x(caixa, ponto), y(caixa, ponto), unidade);
                    rotulo(cs, pagina, fonte, unidade, x(caixa, ponto) + unidade * 1.5f, y(caixa, ponto) + unidade * 1.5f, ponto.getNumero());
                }
            }
        }
    }

    private static float x(PDRectangle caixa, PontoPlanejamento ponto) {
        return caixa.getLowerLeftX() + ponto.getX().floatValue() * caixa.getWidth();
    }

    private static float y(PDRectangle caixa, PontoPlanejamento ponto) {
        return caixa.getUpperRightY() - ponto.getY().floatValue() * caixa.getHeight();
    }

    private static void circulo(PDPageContentStream cs, float cx, float cy, float r) throws IOException {
        float k = 0.5523f * r;
        cs.setNonStrokingColor(EXECUTADO);
        cs.moveTo(cx + r, cy);
        cs.curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r);
        cs.curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy);
        cs.curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r);
        cs.curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy);
        cs.fill();
    }

    /** O texto gira com a rotação da folha, para ficar de pé em quem lê. */
    private static void rotulo(PDPageContentStream cs, PDPage pagina, PDType1Font fonte, float unidade, float px, float py, String texto) throws IOException {
        String seguro = seguro(fonte, texto);
        float tamanho = unidade * 2.2f;
        float largura = fonte.getStringWidth(seguro) / 1000f * tamanho;
        cs.saveGraphicsState();
        cs.transform(Matrix.getRotateInstance(Math.toRadians(pagina.getRotation()), px, py));
        cs.setNonStrokingColor(FUNDO_ROTULO);
        cs.addRect(-tamanho * 0.2f, -tamanho * 0.25f, largura + tamanho * 0.4f, tamanho * 1.2f);
        cs.fill();
        cs.beginText();
        cs.setFont(fonte, tamanho);
        cs.setNonStrokingColor(EXECUTADO);
        cs.newLineAtOffset(0, 0);
        cs.showText(seguro);
        cs.endText();
        cs.restoreGraphicsState();
    }

    private static byte[] salvar(PDDocument documento) throws IOException {
        try (ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            documento.save(saida);
            return saida.toByteArray();
        }
    }

    private static String seguro(PDType1Font fonte, String texto) throws IOException {
        StringBuilder saida = new StringBuilder();
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (Character.isWhitespace(c)) {
                saida.append(' ');
                continue;
            }
            try {
                fonte.encode(String.valueOf(c));
                saida.append(c);
            } catch (IllegalArgumentException e) {
                saida.append('?');
            }
        }
        return saida.toString();
    }

    /** Páginas A4 de texto, com quebra automática. */
    private static final class Escrita {

        private static final float MARGEM = 48;
        private static final float LARGURA = PDRectangle.A4.getWidth() - 2 * MARGEM;
        private static final float BASE = 48;
        private static final float ALTURA_FOTO = 190;
        private static final float ALTURA_LEGENDA = 34;
        private static final Color TEXTO = new Color(0x0f, 0x17, 0x2a);
        private static final Color SUAVE = new Color(0x47, 0x55, 0x69);
        private static final Color FUNDO_CABECALHO = new Color(0xe2, 0xe8, 0xf0);
        private static final Color FUNDO_TOTAL = new Color(0xf1, 0xf5, 0xf9);
        private static final Color DIVISOR = new Color(0xcb, 0xd5, 0xe1);
        private static final float FONTE_TABELA = 7;
        private static final float ALTURA_LINHA = 12;

        private final PDDocument documento;
        private final String cabecalho;
        private final PDType1Font normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font negrito = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private PDPageContentStream cs;
        private float y;

        Escrita(PDDocument documento, String cabecalho) throws IOException {
            this.documento = documento;
            this.cabecalho = cabecalho + " · gerado em " + OffsetDateTime.now(ApontamentoExecucaoUseCases.FUSO).format(DATA_HORA);
            novaPagina();
        }

        void titulo(String texto) throws IOException {
            linhas(texto, negrito, 16, TEXTO, MARGEM, LARGURA, 21);
            y -= 4;
        }

        void secao(String texto) throws IOException {
            garantir(60);
            y -= 12;
            linhas(texto, negrito, 12, TEXTO, MARGEM, LARGURA, 16);
            y -= 2;
        }

        void grupo(String texto) throws IOException {
            garantir(40);
            y -= 6;
            linhas(texto, negrito, 10, SUAVE, MARGEM, LARGURA, 14);
        }

        void paragrafo(String texto) throws IOException {
            linhas(texto, normal, 10, TEXTO, MARGEM, LARGURA, 14);
        }

        void campo(String rotulo, String valor) throws IOException {
            garantir(28);
            linhas(rotulo, normal, 9, SUAVE, MARGEM, LARGURA, 12);
            linhas(valor, negrito, 10, TEXTO, MARGEM, LARGURA, 14);
            y -= 3;
        }

        /** Duas fotos por linha, cada uma com o item, a legenda e quem lançou. */
        void fotos(List<Foto> fotos) throws IOException {
            float vao = 12;
            float largura = (LARGURA - vao) / 2;
            for (int i = 0; i < fotos.size(); i += 2) {
                List<Foto> linha = fotos.subList(i, Math.min(i + 2, fotos.size()));
                garantir(ALTURA_FOTO + ALTURA_LEGENDA);
                float topo = y;
                float x = MARGEM;
                for (Foto foto : linha) {
                    foto(foto, x, topo, largura);
                    x += largura + vao;
                }
                y = topo - ALTURA_FOTO - ALTURA_LEGENDA;
            }
        }

        private void foto(Foto foto, float x, float topo, float largura) throws IOException {
            PDImageXObject imagem = PDImageXObject.createFromByteArray(documento, foto.jpeg(), foto.item());
            float escala = Math.min(largura / imagem.getWidth(), ALTURA_FOTO / imagem.getHeight());
            float l = imagem.getWidth() * escala;
            float a = imagem.getHeight() * escala;
            cs.drawImage(imagem, x, topo - a, l, a);
            y = topo - a - 3;
            legenda(foto.legenda() != null ? foto.item() + " · " + foto.legenda() : foto.item(), negrito, TEXTO, x, largura);
            legenda(foto.autoria(), normal, SUAVE, x, largura);
        }

        /** Legenda em uma linha só, cortada se passar da largura da foto, para a linha de fotos não mudar de altura. */
        private void legenda(String texto, PDType1Font fonte, Color cor, float x, float largura) throws IOException {
            String linha = quebrar(seguro(fonte, texto), fonte, 8, largura).getFirst();
            cs.beginText();
            cs.setFont(fonte, 8);
            cs.setNonStrokingColor(cor);
            cs.newLineAtOffset(x, y - 8);
            cs.showText(linha);
            cs.endText();
            y -= 11;
        }

        void nota(String texto) throws IOException {
            linhas(texto, normal, 8, SUAVE, MARGEM, LARGURA, 11);
        }

        /**
         * Tabela com o cabeçalho repetido a cada página. Cada célula ocupa uma linha; o texto que não cabe é cortado.
         * {@code total}, quando houver, fecha a tabela em negrito.
         */
        void tabela(String[] cabecalho, float[] larguras, boolean[] direita, List<String[]> linhas, String[] total) throws IOException {
            garantir(ALTURA_LINHA * 3);
            y -= 4;
            linhaDaTabela(cabecalho, larguras, direita, negrito, FUNDO_CABECALHO);
            for (String[] linha : linhas) {
                if (y - ALTURA_LINHA < BASE) {
                    cs.close();
                    novaPagina();
                    linhaDaTabela(cabecalho, larguras, direita, negrito, FUNDO_CABECALHO);
                }
                linhaDaTabela(linha, larguras, direita, normal, null);
            }
            if (total != null) {
                garantir(ALTURA_LINHA);
                linhaDaTabela(total, larguras, direita, negrito, FUNDO_TOTAL);
            }
            y -= 6;
        }

        private void linhaDaTabela(String[] celulas, float[] larguras, boolean[] direita, PDType1Font fonte, Color fundo) throws IOException {
            float largura = 0;
            for (float l : larguras) {
                largura += l;
            }
            if (fundo != null) {
                cs.setNonStrokingColor(fundo);
                cs.addRect(MARGEM, y - ALTURA_LINHA, largura, ALTURA_LINHA);
                cs.fill();
            }
            float x = MARGEM;
            for (int i = 0; i < larguras.length; i++) {
                String texto = cortar(seguro(fonte, i < celulas.length && celulas[i] != null ? celulas[i] : ""), fonte, larguras[i] - 4);
                float largo = fonte.getStringWidth(texto) / 1000f * FONTE_TABELA;
                cs.beginText();
                cs.setFont(fonte, FONTE_TABELA);
                cs.setNonStrokingColor(TEXTO);
                cs.newLineAtOffset(direita[i] ? x + larguras[i] - 2 - largo : x + 2, y - ALTURA_LINHA + 3.5f);
                cs.showText(texto);
                cs.endText();
                x += larguras[i];
            }
            cs.setStrokingColor(DIVISOR);
            cs.setLineWidth(0.4f);
            cs.moveTo(MARGEM, y - ALTURA_LINHA);
            cs.lineTo(MARGEM + largura, y - ALTURA_LINHA);
            cs.stroke();
            y -= ALTURA_LINHA;
        }

        private static String cortar(String texto, PDType1Font fonte, float largura) throws IOException {
            if (fonte.getStringWidth(texto) / 1000f * FONTE_TABELA <= largura) {
                return texto;
            }
            String corte = texto;
            while (!corte.isEmpty() && fonte.getStringWidth(corte + "...") / 1000f * FONTE_TABELA > largura) {
                corte = corte.substring(0, corte.length() - 1);
            }
            return corte.stripTrailing() + "...";
        }

        /** Duas linhas de assinatura lado a lado, com o nome embaixo. */
        void assinaturas(String esquerda, String direita) throws IOException {
            garantir(90);
            y -= 60;
            float largura = (LARGURA - 40) / 2;
            float x = MARGEM;
            for (String nome : new String[]{esquerda, direita}) {
                cs.setStrokingColor(TEXTO);
                cs.setLineWidth(0.6f);
                cs.moveTo(x, y);
                cs.lineTo(x + largura, y);
                cs.stroke();
                String texto = cortar(seguro(normal, nome), normal, largura);
                float largo = normal.getStringWidth(texto) / 1000f * FONTE_TABELA;
                cs.beginText();
                cs.setFont(normal, FONTE_TABELA);
                cs.setNonStrokingColor(SUAVE);
                cs.newLineAtOffset(x + (largura - largo) / 2, y - 11);
                cs.showText(texto);
                cs.endText();
                x += largura + 40;
            }
            y -= 20;
        }

        void fechar() throws IOException {
            cs.close();
        }

        private void linhas(String texto, PDType1Font fonte, float tamanho, Color cor, float x, float largura, float entrelinha) throws IOException {
            for (String linha : quebrar(seguro(fonte, texto), fonte, tamanho, largura)) {
                garantir(entrelinha);
                cs.beginText();
                cs.setFont(fonte, tamanho);
                cs.setNonStrokingColor(cor);
                cs.newLineAtOffset(x, y - tamanho);
                cs.showText(linha);
                cs.endText();
                y -= entrelinha;
            }
        }

        private static List<String> quebrar(String texto, PDType1Font fonte, float tamanho, float largura) throws IOException {
            List<String> linhas = new ArrayList<>();
            StringBuilder atual = new StringBuilder();
            for (String palavra : texto.split(" ")) {
                String tentativa = atual.isEmpty() ? palavra : atual + " " + palavra;
                if (fonte.getStringWidth(tentativa) / 1000f * tamanho <= largura) {
                    atual = new StringBuilder(tentativa);
                    continue;
                }
                if (!atual.isEmpty()) {
                    linhas.add(atual.toString());
                }
                atual = new StringBuilder();
                String resto = palavra;
                while (fonte.getStringWidth(resto) / 1000f * tamanho > largura) {
                    int corte = resto.length() - 1;
                    while (corte > 1 && fonte.getStringWidth(resto.substring(0, corte)) / 1000f * tamanho > largura) {
                        corte--;
                    }
                    linhas.add(resto.substring(0, corte));
                    resto = resto.substring(corte);
                }
                atual.append(resto);
            }
            if (!atual.isEmpty()) {
                linhas.add(atual.toString());
            }
            return linhas;
        }

        private void garantir(float altura) throws IOException {
            if (y - altura < BASE) {
                cs.close();
                novaPagina();
            }
        }

        private void novaPagina() throws IOException {
            PDPage pagina = new PDPage(PDRectangle.A4);
            documento.addPage(pagina);
            cs = new PDPageContentStream(documento, pagina);
            cs.beginText();
            cs.setFont(normal, 8);
            cs.setNonStrokingColor(SUAVE);
            cs.newLineAtOffset(MARGEM, PDRectangle.A4.getHeight() - 30);
            cs.showText(seguro(normal, cabecalho));
            cs.endText();
            y = PDRectangle.A4.getHeight() - 48;
        }
    }
}
