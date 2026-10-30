package com.nexo.app.modules.estoque.application.services;

import com.nexo.app.modules.estoque.domain.MovimentacaoEstoque;
import com.nexo.app.modules.estoque.infrastructure.persistence.MovimentacaoEstoqueRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.storage.ObjectStorageService;
import com.nexo.app.shared.infrastructure.storage.StorageObject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Mantem assinaturas digitais de transferencias fora do banco e fornece sua leitura autenticada. */
@Service
@RequiredArgsConstructor
public class AssinaturaTransferenciaService {

    private static final String PREFIXO_OBJETO = "objeto:";
    private static final String PREFIXO_DATA_PNG = "data:image/png;base64,";
    private static final int TAMANHO_MAXIMO_BYTES = 196_608;

    private final ObjectStorageService armazenamento;
    private final MovimentacaoEstoqueRepository movimentacoes;

    public AssinaturasTransferenciaArmazenadas armazenar(UUID idReferencia, String entregador, String recebedor) {
        String chaveEntregador = armazenarUma(idReferencia, ParteTransferencia.ENTREGADOR, entregador);
        try {
            String chaveRecebedor = armazenarUma(idReferencia, ParteTransferencia.RECEBEDOR, recebedor);
            return new AssinaturasTransferenciaArmazenadas(chaveEntregador, chaveRecebedor);
        } catch (RuntimeException erro) {
            removerSeObjeto(chaveEntregador);
            throw erro;
        }
    }

    @Transactional(readOnly = true)
    public Optional<AssinaturaArquivo> baixar(UUID idReferencia, ParteTransferencia parte) {
        List<MovimentacaoEstoque> lista = movimentacoes.findAllByIdReferenciaOperacaoInOrderByDataHoraAscIdAsc(List.of(idReferencia));
        if (lista.isEmpty()) {
            throw new RecursoNaoEncontradoException("Operação de movimentação não encontrada");
        }
        MovimentacaoEstoque base = lista.get(0);
        String referencia = parte == ParteTransferencia.ENTREGADOR
                ? base.getAssinaturaEntregador()
                : base.getAssinaturaRecebedor();

        if (referencia == null || referencia.isBlank() || "FISICA".equalsIgnoreCase(referencia)) {
            return Optional.empty();
        }
        if (referencia.startsWith(PREFIXO_DATA_PNG)) {
            byte[] bytes = decodificarPng(referencia);
            return Optional.of(new AssinaturaArquivo(
                    new ByteArrayInputStream(bytes), "image/png", bytes.length));
        }
        if (!referencia.startsWith(PREFIXO_OBJETO)) {
            return Optional.empty();
        }
        StorageObject objeto = armazenamento.baixar(referencia.substring(PREFIXO_OBJETO.length()));
        return Optional.of(new AssinaturaArquivo(
                objeto.conteudo(), normalizarContentType(objeto.contentType()), objeto.tamanhoBytes()));
    }

    private String armazenarUma(UUID idReferencia, ParteTransferencia parte, String valor) {
        if (valor == null || valor.isBlank() || "FISICA".equalsIgnoreCase(valor)) {
            return valor;
        }
        if (!valor.startsWith("data:image/")) {
            return valor;
        }
        byte[] bytes = decodificarPng(valor);
        String chave = "estoque/transferencias/%s/assinaturas/%s-%s.png".formatted(
                idReferencia, UUID.randomUUID(), parte.name().toLowerCase(Locale.ROOT));
        armazenamento.enviar(chave, new ByteArrayInputStream(bytes), bytes.length, "image/png");
        return PREFIXO_OBJETO + chave;
    }

    private byte[] decodificarPng(String valor) {
        if (!valor.startsWith(PREFIXO_DATA_PNG)) {
            throw new InvarianteVioladaException("A assinatura digital deve ser uma imagem PNG valida.");
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(valor.substring(PREFIXO_DATA_PNG.length()));
            if (bytes.length == 0 || bytes.length > TAMANHO_MAXIMO_BYTES || !ehPng(bytes)) {
                throw new InvarianteVioladaException("A assinatura digital deve ser uma imagem PNG valida de ate 192 KB.");
            }
            return bytes;
        } catch (IllegalArgumentException erro) {
            throw new InvarianteVioladaException("A assinatura digital possui codificacao invalida.");
        }
    }

    private static boolean ehPng(byte[] bytes) {
        return bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e
                && bytes[3] == 0x47 && bytes[4] == 0x0d && bytes[5] == 0x0a
                && bytes[6] == 0x1a && bytes[7] == 0x0a;
    }

    private void removerSeObjeto(String referencia) {
        if (referencia != null && referencia.startsWith(PREFIXO_OBJETO)) {
            armazenamento.remover(referencia.substring(PREFIXO_OBJETO.length()));
        }
    }

    private static String normalizarContentType(String contentType) {
        return contentType == null || contentType.isBlank() ? "image/png" : contentType;
    }

    public enum ParteTransferencia { ENTREGADOR, RECEBEDOR }

    public record AssinaturasTransferenciaArmazenadas(String entregador, String recebedor) {}

    public record AssinaturaArquivo(InputStream conteudo, String contentType, long tamanhoBytes) {}
}
