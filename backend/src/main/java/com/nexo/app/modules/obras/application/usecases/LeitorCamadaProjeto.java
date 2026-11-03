package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê do próprio PDF o que não precisa de IA: códigos dos postes, caixas de estrutura e escala. O texto é exato e fica
 * ao lado do símbolo. Em muitos projetos o desenho é uma imagem embutida, então a posição do símbolo e as ligações
 * ficam com o modelo; quando não há camada de texto, a lista vem vazia e a leitura toda fica com ele.
 *
 * <p>As posições ficam em [0,1] da página como ela aparece, com a rotação do próprio PDF e sem a rotação salva.</p>
 */
@Component
public class LeitorCamadaProjeto {

    /** Letra seguida de 5 a 7 dígitos: é como os postes aparecem nos projetos (ex.: D381272, B180902). */
    private static final Pattern CODIGO_PONTO = Pattern.compile("^[A-Z]\\d{5,7}$");
    /** Altura/esforço do poste (ex.: 11/400, 12/600), que abre as caixas de estrutura. */
    private static final Pattern POSTE = Pattern.compile("^\\d{1,2}/\\d{3,4}$");
    /** Metragem e condutor escritos ao lado da rede não são estruturas do poste. */
    private static final Pattern TEXTO_DE_VAO = Pattern.compile("^\\d+[.,]\\d+\\s*M$|AWG|MM2|MM²|XLPE|#|\\dX\\d", Pattern.CASE_INSENSITIVE);
    private static final Pattern ESCALA = Pattern.compile("ESCALA\\s*:?\\s*1\\s*:\\s*([\\d.]+)", Pattern.CASE_INSENSITIVE);

    private static final double ALCANCE_CAIXA_PT = 60;
    private static final int MAXIMO_LINHAS_CAIXA = 10;

    public record TextoFolha(String texto, double x, double y, double fimX, double altura) {
    }

    /**
     * @param codigo     identificador do poste na folha; nulo em caixa de estrutura sem código, que costuma ser poste novo
     * @param x          posição da etiqueta ou da caixa, próxima do símbolo
     * @param estruturas poste e estruturas da caixa ligada ao ponto
     */
    public record Candidato(String codigo, double x, double y, List<String> estruturas) {
    }

    /**
     * @param larguraPt tamanho da página como ela aparece, em pontos, para calcular distâncias
     * @param escala    denominador da escala do carimbo (1000 em 1:1.000), nulo quando não há
     */
    public record CamadaPagina(List<Candidato> candidatos, Integer escala, double larguraPt, double alturaPt) {
    }

    public CamadaPagina ler(byte[] pdf, int pagina) {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            if (pagina < 1 || pagina > documento.getNumberOfPages()) {
                throw new InvarianteVioladaException("O projeto não tem a página " + pagina + ".");
            }
            PDPage page = documento.getPage(pagina - 1);
            PDRectangle caixa = page.getCropBox();
            int rotacao = ((page.getRotation() % 360) + 360) % 360;
            boolean deitada = rotacao == 90 || rotacao == 270;
            double larguraPt = deitada ? caixa.getHeight() : caixa.getWidth();
            double alturaPt = deitada ? caixa.getWidth() : caixa.getHeight();

            List<TextoFolha> textos = lerTextos(documento, pagina, larguraPt, alturaPt);
            return new CamadaPagina(montarCandidatos(textos, larguraPt, alturaPt), lerEscala(documento, pagina),
                    larguraPt, alturaPt);
        } catch (IOException e) {
            throw new InvarianteVioladaException("Não foi possível ler o PDF do projeto.");
        }
    }

    private static List<TextoFolha> lerTextos(PDDocument documento, int pagina, double larguraPt, double alturaPt) throws IOException {
        List<TextoFolha> textos = new ArrayList<>();
        PDFTextStripper leitor = new PDFTextStripper() {
            @Override
            protected void writeString(String texto, List<TextPosition> posicoes) {
                if (texto.isBlank() || posicoes.isEmpty()) {
                    return;
                }
                TextPosition inicio = posicoes.get(0);
                TextPosition fim = posicoes.get(posicoes.size() - 1);
                // Só texto na horizontal: nomes de condutor e o carimbo girado não identificam ponto.
                if (Math.abs(inicio.getDir()) > 1 && Math.abs(inicio.getDir() - 360) > 1) {
                    return;
                }
                textos.add(new TextoFolha(texto.trim(), inicio.getX() / larguraPt, inicio.getY() / alturaPt,
                        (fim.getX() + fim.getWidth()) / larguraPt, inicio.getHeight() / alturaPt));
            }
        };
        leitor.setSortByPosition(true);
        leitor.setStartPage(pagina);
        leitor.setEndPage(pagina);
        leitor.writeText(documento, new StringWriter());
        return textos;
    }

    private static Integer lerEscala(PDDocument documento, int pagina) throws IOException {
        PDFTextStripper leitor = new PDFTextStripper();
        leitor.setStartPage(pagina);
        leitor.setEndPage(pagina);
        return interpretarEscala(leitor.getText(documento));
    }

    static Integer interpretarEscala(String texto) {
        Matcher achado = ESCALA.matcher(texto);
        if (!achado.find()) {
            return null;
        }
        try {
            int valor = Integer.parseInt(achado.group(1).replace(".", ""));
            return valor >= 50 && valor <= 100_000 ? valor : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static List<Candidato> montarCandidatos(List<TextoFolha> textos, double larguraPt, double alturaPt) {
        List<CaixaEstrutura> caixas = lerCaixas(textos, alturaPt);
        List<Candidato> candidatos = new ArrayList<>();
        List<CaixaEstrutura> usadas = new ArrayList<>();
        for (TextoFolha texto : textos) {
            String codigo = texto.texto().toUpperCase(Locale.ROOT);
            if (!CODIGO_PONTO.matcher(codigo).matches()) {
                continue;
            }
            CaixaEstrutura caixa = caixaMaisProxima(texto.x(), texto.y(), caixas, usadas, larguraPt, alturaPt);
            if (caixa != null) {
                usadas.add(caixa);
            }
            candidatos.add(new Candidato(codigo, texto.x(), texto.y(), caixa != null ? caixa.textos() : List.of()));
        }
        for (CaixaEstrutura caixa : caixas) {
            if (!usadas.contains(caixa)) {
                candidatos.add(new Candidato(null, caixa.x(), caixa.y(), caixa.textos()));
            }
        }
        return candidatos;
    }

    private record CaixaEstrutura(double x, double y, List<String> textos) {
    }

    /** O poste abre a caixa e as estruturas seguem nas linhas de baixo, quase na mesma coluna. */
    private static List<CaixaEstrutura> lerCaixas(List<TextoFolha> textos, double alturaPt) {
        List<CaixaEstrutura> caixas = new ArrayList<>();
        for (TextoFolha inicio : textos) {
            if (!POSTE.matcher(inicio.texto()).matches()) {
                continue;
            }
            List<TextoFolha> abaixo = textos.stream()
                    .filter(t -> t != inicio && t.y() > inicio.y())
                    // Código de ponto vizinho não faz parte da caixa.
                    .filter(t -> !CODIGO_PONTO.matcher(t.texto()).matches())
                    .filter(t -> !TEXTO_DE_VAO.matcher(t.texto()).find())
                    .filter(t -> t.y() - inicio.y() < (MAXIMO_LINHAS_CAIXA * 14) / alturaPt)
                    .filter(t -> t.x() > inicio.x() - 0.06 && t.x() < inicio.fimX() + 0.06)
                    .sorted(Comparator.comparingDouble(TextoFolha::y).thenComparingDouble(TextoFolha::x))
                    .toList();
            List<String> partes = new ArrayList<>();
            partes.add(inicio.texto());
            double ultimoY = inicio.y();
            StringBuilder linha = new StringBuilder();
            double linhaY = -1;
            for (TextoFolha t : abaixo) {
                if (t.y() - ultimoY > 22 / alturaPt) {
                    break;
                }
                if (linhaY >= 0 && Math.abs(t.y() - linhaY) > 3 / alturaPt) {
                    adicionarEstrutura(partes, linha.toString());
                    linha.setLength(0);
                }
                linha.append(linha.isEmpty() ? "" : " ").append(t.texto());
                linhaY = t.y();
                ultimoY = t.y();
            }
            adicionarEstrutura(partes, linha.toString());
            caixas.add(new CaixaEstrutura(inicio.x(), inicio.y(), List.copyOf(partes)));
        }
        return caixas;
    }

    private static void adicionarEstrutura(List<String> partes, String linha) {
        String limpa = linha.replaceFirst("^\\s*;\\s*", "").trim();
        if (limpa.length() >= 2) {
            partes.add(limpa);
        }
    }

    private static CaixaEstrutura caixaMaisProxima(double x, double y, List<CaixaEstrutura> caixas, List<CaixaEstrutura> usadas,
                                                   double larguraPt, double alturaPt) {
        return caixas.stream()
                .filter(c -> !usadas.contains(c))
                .filter(c -> Math.hypot((c.x() - x) * larguraPt, (c.y() - y) * alturaPt) < ALCANCE_CAIXA_PT)
                .min(Comparator.comparingDouble(c -> Math.hypot((c.x() - x) * larguraPt, (c.y() - y) * alturaPt)))
                .orElse(null);
    }
}
