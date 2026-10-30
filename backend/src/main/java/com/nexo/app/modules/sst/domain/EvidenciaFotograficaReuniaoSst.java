package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.AuditIgnore;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.persistence.CryptoConverterImpl;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/** Fotografia privada opcional; não integra os insumos enviados ao provedor de IA. */
@Entity
@Table(name = "evidencias_fotograficas_reuniao_sst")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvidenciaFotograficaReuniaoSst extends BaseEntity {

    @Column(name = "id_reuniao", nullable = false, updatable = false)
    private UUID idReuniao;

    @AuditIgnore
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "nome_arquivo", nullable = false, updatable = false, length = 1024)
    private String nomeArquivo;

    @Column(name = "content_type", nullable = false, updatable = false, length = 120)
    private String contentType;

    @Column(name = "tamanho_bytes", nullable = false, updatable = false)
    private long tamanhoBytes;

    @AuditIgnore
    @Column(name = "object_key", nullable = false, updatable = false, length = 500)
    private String objectKey;

    @Column(name = "sha256", nullable = false, updatable = false, length = 64)
    private String sha256;

    @AuditIgnore
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "legenda", length = 2048)
    private String legenda;

    @Column(name = "id_autor", nullable = false, updatable = false)
    private UUID idAutor;

    @Column(name = "enviada_em", nullable = false, updatable = false)
    private OffsetDateTime enviadaEm;

    @Column(name = "removida_em")
    private OffsetDateTime removidaEm;

    @Column(name = "id_usuario_remocao")
    private UUID idUsuarioRemocao;

    public EvidenciaFotograficaReuniaoSst(UUID idReuniao, String nomeArquivo, String contentType,
                                           long tamanhoBytes, String objectKey, String sha256,
                                           String legenda, UUID idAutor) {
        this.idReuniao = Objects.requireNonNull(idReuniao, "A reunião é obrigatória.");
        this.nomeArquivo = exigir(nomeArquivo, 255, "O nome do arquivo é obrigatório.");
        this.contentType = exigir(contentType, 120, "O tipo da imagem é obrigatório.");
        if (tamanhoBytes < 1) throw new InvarianteVioladaException("A imagem está vazia.");
        this.tamanhoBytes = tamanhoBytes;
        this.objectKey = exigir(objectKey, 500, "A chave privada é obrigatória.");
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new InvarianteVioladaException("O hash da evidência é inválido.");
        }
        this.sha256 = sha256;
        this.legenda = opcional(legenda, 500);
        this.idAutor = Objects.requireNonNull(idAutor, "Não foi possível identificar o autor.");
        this.enviadaEm = OffsetDateTime.now();
    }

    public void remover(UUID idUsuario) {
        if (!isAtivo()) throw new InvarianteVioladaException("A evidência já foi removida.");
        this.idUsuarioRemocao = Objects.requireNonNull(idUsuario,
                "Não foi possível identificar quem removeu a evidência.");
        this.removidaEm = OffsetDateTime.now();
        desativar();
    }

    public boolean pertenceA(UUID reuniaoId) {
        return idReuniao.equals(reuniaoId);
    }

    public boolean foiEnviadaPor(UUID usuarioId) {
        return idAutor.equals(usuarioId);
    }

    private static String exigir(String valor, int maximo, String mensagem) {
        if (valor == null || valor.isBlank()) throw new InvarianteVioladaException(mensagem);
        String texto = valor.trim();
        if (texto.length() > maximo) throw new InvarianteVioladaException(mensagem);
        return texto;
    }

    private static String opcional(String valor, int maximo) {
        return valor == null || valor.isBlank() ? null : exigir(valor, maximo, "A legenda é inválida.");
    }
}
