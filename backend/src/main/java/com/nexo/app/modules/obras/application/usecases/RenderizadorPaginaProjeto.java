package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Transforma uma página do projeto em imagens para o modelo. Folhas grandes viram blocos sobrepostos, para que
 * balões e cotas continuem legíveis, com a posição de cada bloco em [0,1] da página.
 */
@Component
public class RenderizadorPaginaProjeto {

    private static final float DPI = 200f;
    private static final int MAIOR_LADO_PAGINA = 4000;
    private static final int MAIOR_LADO_BLOCO = 1600;
    private static final int MAXIMO_POR_EIXO = 3;
    private static final double SOBREPOSICAO = 0.12;

    public record Bloco(double inicioX, double inicioY, double largura, double altura, String base64Jpeg) {
    }

    public List<Bloco> renderizar(byte[] pdf, int pagina) {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            if (pagina < 1 || pagina > documento.getNumberOfPages()) {
                throw new InvarianteVioladaException("O projeto não tem a página " + pagina + ".");
            }
            var caixa = documento.getPage(pagina - 1).getBBox();
            float maiorLadoEmPontos = Math.max(caixa.getWidth(), caixa.getHeight());
            float dpi = Math.min(DPI, MAIOR_LADO_PAGINA * 72f / maiorLadoEmPontos);
            BufferedImage imagem = new PDFRenderer(documento).renderImageWithDPI(pagina - 1, dpi, ImageType.RGB);
            return dividir(imagem);
        } catch (IOException e) {
            throw new InvarianteVioladaException("Não foi possível ler o PDF do projeto.");
        }
    }

    private static List<Bloco> dividir(BufferedImage imagem) throws IOException {
        int colunas = quantidade(imagem.getWidth());
        int linhas = quantidade(imagem.getHeight());
        double larguraBloco = tamanhoRelativo(colunas);
        double alturaBloco = tamanhoRelativo(linhas);
        List<Bloco> blocos = new ArrayList<>();
        for (int linha = 0; linha < linhas; linha++) {
            for (int coluna = 0; coluna < colunas; coluna++) {
                double inicioX = inicio(coluna, colunas, larguraBloco);
                double inicioY = inicio(linha, linhas, alturaBloco);
                BufferedImage recorte = imagem.getSubimage(
                        (int) (inicioX * imagem.getWidth()),
                        (int) (inicioY * imagem.getHeight()),
                        Math.min((int) Math.ceil(larguraBloco * imagem.getWidth()), imagem.getWidth() - (int) (inicioX * imagem.getWidth())),
                        Math.min((int) Math.ceil(alturaBloco * imagem.getHeight()), imagem.getHeight() - (int) (inicioY * imagem.getHeight())));
                blocos.add(new Bloco(inicioX, inicioY, larguraBloco, alturaBloco, paraJpeg(recorte)));
            }
        }
        return blocos;
    }

    static int quantidade(int tamanho) {
        return Math.min(MAXIMO_POR_EIXO, Math.max(1, (int) Math.ceil(tamanho / (double) MAIOR_LADO_BLOCO)));
    }

    /** Tamanho do bloco em [0,1] para cobrir a página com a sobreposição definida. */
    static double tamanhoRelativo(int quantidade) {
        return quantidade == 1 ? 1d : 1d / (quantidade - (quantidade - 1) * SOBREPOSICAO);
    }

    static double inicio(int indice, int quantidade, double tamanho) {
        return quantidade == 1 ? 0d : indice * (1d - tamanho) / (quantidade - 1);
    }

    private static String paraJpeg(BufferedImage imagem) throws IOException {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(imagem, "jpeg", saida);
        return Base64.getEncoder().encodeToString(saida.toByteArray());
    }
}
