package com.nexo.app.shared.infrastructure.web;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Requisição cujo corpo já foi lido e pode ser lido de novo.
 *
 * <p>Existe porque o {@link IdempotenciaFilter} precisa digerir o corpo <b>antes</b> de
 * decidir se executa — e o {@code InputStream} de uma requisição só serve uma leitura.
 * Sem este wrapper, o handler receberia um corpo vazio.</p>
 *
 * <p>Não serve para multipart: o filtro não bufferiza esses corpos justamente para não
 * duplicar 15 MB de anexo em memória.</p>
 *
 * @author <b>Thalisson</b>
 */
class CorpoBufferizadoRequest extends HttpServletRequestWrapper {

    private final byte[] corpo;

    CorpoBufferizadoRequest(HttpServletRequest request, byte[] corpo) {
        super(request);
        this.corpo = corpo;
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream fonte = new ByteArrayInputStream(corpo);
        return new ServletInputStream() {
            @Override
            public int read() {
                return fonte.read();
            }

            @Override
            public int read(byte[] destino, int offset, int tamanho) {
                return fonte.read(destino, offset, tamanho);
            }

            @Override
            public int available() {
                return fonte.available();
            }

            @Override
            public boolean isFinished() {
                return fonte.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // Leitura não-bloqueante não se aplica: o corpo inteiro já está em memória.
                throw new UnsupportedOperationException("Corpo já bufferizado; leitura assíncrona não se aplica.");
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        Charset charset = getCharacterEncoding() == null
                ? StandardCharsets.UTF_8
                : Charset.forName(getCharacterEncoding());
        return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }

    @Override
    public int getContentLength() {
        return corpo.length;
    }

    @Override
    public long getContentLengthLong() {
        return corpo.length;
    }
}
