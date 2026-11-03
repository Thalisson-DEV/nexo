package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FotoEvidenciaTest {

    /** Foto de celular em pé: gravada deitada, com o topo real à esquerda e a indicação de girar 90 graus no EXIF. */
    private static byte[] jpegComOrientacao(int orientacao) throws IOException {
        BufferedImage deitada = new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 400; x++) {
            for (int y = 0; y < 200; y++) {
                deitada.setRGB(x, y, x < 200 ? Color.RED.getRGB() : Color.BLUE.getRGB());
            }
        }
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        ImageIO.write(deitada, "jpeg", jpeg);
        byte[] b = jpeg.toByteArray();

        byte[] tiff = {'I', 'I', 42, 0, 8, 0, 0, 0, 1, 0, 0x12, 0x01, 3, 0, 1, 0, 0, 0, (byte) orientacao, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        int tamanho = 2 + 6 + tiff.length;
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        saida.write(b, 0, 2);
        saida.write(new byte[]{(byte) 0xFF, (byte) 0xE1, (byte) (tamanho >> 8), (byte) tamanho, 'E', 'x', 'i', 'f', 0, 0});
        saida.write(tiff);
        saida.write(b, 2, b.length - 2);
        return saida.toByteArray();
    }

    @Test
    void endireitaAFotoDeCelularPeloExif() throws IOException {
        byte[] foto = jpegComOrientacao(6);

        assertThat(FotoEvidencia.orientacaoExif(foto)).isEqualTo(6);
        BufferedImage lida = FotoEvidencia.ler(foto);

        assertThat(lida.getWidth()).isEqualTo(200);
        assertThat(lida.getHeight()).isEqualTo(400);
        Color topo = new Color(lida.getRGB(100, 20));
        assertThat(topo.getRed()).isGreaterThan(topo.getBlue());
    }

    @Test
    void semExifMantemAFoto() throws IOException {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(30, 20, BufferedImage.TYPE_INT_ARGB), "png", png);

        BufferedImage lida = FotoEvidencia.ler(png.toByteArray());

        assertThat(FotoEvidencia.orientacaoExif(png.toByteArray())).isEqualTo(1);
        assertThat(lida.getWidth()).isEqualTo(30);
    }

    @Test
    void recusaQuemNaoEFoto() {
        assertThatThrownBy(() -> FotoEvidencia.ler("isto não é foto".getBytes()))
                .isInstanceOf(InvarianteVioladaException.class).hasMessageContaining("JPG ou PNG");
    }

    @Test
    void reduzAoMaiorLadoSemAumentarFotoPequena() throws IOException {
        BufferedImage reduzida = ImageIO.read(new ByteArrayInputStream(FotoEvidencia.jpeg(new BufferedImage(2000, 1000, BufferedImage.TYPE_INT_RGB), 480, 0.75f)));
        BufferedImage pequena = ImageIO.read(new ByteArrayInputStream(FotoEvidencia.jpeg(new BufferedImage(100, 50, BufferedImage.TYPE_INT_RGB), 480, 0.75f)));

        assertThat(reduzida.getWidth()).isEqualTo(480);
        assertThat(reduzida.getHeight()).isEqualTo(240);
        assertThat(pequena.getWidth()).isEqualTo(100);
    }
}
