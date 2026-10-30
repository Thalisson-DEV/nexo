package com.nexo.app.modules.estoque.application.services;

import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.OperacaoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.TipoOperacaoSolicitacao;
import com.nexo.app.modules.estoque.infrastructure.persistence.OperacaoSolicitacaoMaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SolicitacaoMaterialRepository;
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

/** Mantem assinaturas digitais fora do banco e fornece sua leitura autenticada. */
@Service
@RequiredArgsConstructor
public class AssinaturaSolicitacaoMaterialService {

    private static final String PREFIXO_OBJETO = "objeto:";
    private static final String PREFIXO_DATA_PNG = "data:image/png;base64,";
    private static final int TAMANHO_MAXIMO_BYTES = 196_608;

    private final ObjectStorageService armazenamento;
    private final SolicitacaoMaterialRepository solicitacoes;
    private final OperacaoSolicitacaoMaterialRepository operacoes;

    public AssinaturasArmazenadas armazenar(UUID idSolicitacao, String almox, String equipe) {
        String chaveAlmox = armazenarUma(idSolicitacao, Parte.ALMOX, almox);
        try {
            String chaveEquipe = armazenarUma(idSolicitacao, Parte.EQUIPE, equipe);
            return new AssinaturasArmazenadas(chaveAlmox, chaveEquipe);
        } catch (RuntimeException erro) {
            removerSeObjeto(chaveAlmox);
            throw erro;
        }
    }

    @Transactional(readOnly = true)
    public Optional<AssinaturaArquivo> baixar(UUID idSolicitacao, Parte parte) {
        SolicitacaoMaterial solicitacao = solicitacoes.findById(idSolicitacao)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Solicitacao de material nao encontrada"));
        String referencia = referenciaDaAssinatura(solicitacao, parte);
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

    private String referenciaDaAssinatura(SolicitacaoMaterial solicitacao, Parte parte) {
        if (solicitacao.getContexto() == ContextoSolicitacaoMaterial.COMERCIAL) {
            return parte == Parte.ALMOX ? solicitacao.getAssinaturaAlmox() : solicitacao.getAssinaturaEquipe();
        }
        List<OperacaoSolicitacaoMaterial> historico =
                operacoes.findAllBySolicitacaoIdOrderByRealizadaEmAscIdAsc(solicitacao.getId());
        for (int indice = historico.size() - 1; indice >= 0; indice--) {
            OperacaoSolicitacaoMaterial operacao = historico.get(indice);
            if (operacao.getTipo() == TipoOperacaoSolicitacao.ENTREGA) {
                return parte == Parte.ALMOX
                        ? operacao.getAssinaturaAlmox()
                        : operacao.getAssinaturaEquipe();
            }
        }
        return null;
    }

    private String armazenarUma(UUID idSolicitacao, Parte parte, String valor) {
        if (valor == null || valor.isBlank() || "FISICA".equalsIgnoreCase(valor)) {
            return valor;
        }
        // Mantem compatibilidade com clientes legados; a interface atual sempre envia PNG.
        if (!valor.startsWith("data:image/")) {
            return valor;
        }
        byte[] bytes = decodificarPng(valor);
        String chave = "estoque/solicitacoes/%s/assinaturas/%s-%s.png".formatted(
                idSolicitacao, UUID.randomUUID(), parte.name().toLowerCase(Locale.ROOT));
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

    public enum Parte { ALMOX, EQUIPE }

    public record AssinaturasArmazenadas(String almox, String equipe) {}

    public record AssinaturaArquivo(InputStream conteudo, String contentType, long tamanhoBytes) {}
}
