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

/** Metadados da cópia assinada, sempre privada e distinta de evidências fotográficas. */
@Entity
@Table(name = "copias_assinadas_ata_sst")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CopiaAssinadaAtaSst extends BaseEntity {

    @Column(name = "id_reuniao", nullable = false, updatable = false)
    private UUID idReuniao;

    @Column(name = "id_versao_ata", nullable = false, updatable = false)
    private UUID idVersaoAta;

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

    @Column(name = "id_usuario_upload", nullable = false, updatable = false)
    private UUID idUsuarioUpload;

    @Column(name = "enviada_em", nullable = false, updatable = false)
    private OffsetDateTime enviadaEm;

    public CopiaAssinadaAtaSst(UUID idReuniao, UUID idVersaoAta, String nomeArquivo,
                               String contentType, long tamanhoBytes, String objectKey,
                               String sha256, UUID idUsuarioUpload) {
        this.idReuniao = Objects.requireNonNull(idReuniao, "A reunião é obrigatória.");
        this.idVersaoAta = Objects.requireNonNull(idVersaoAta, "A versão aprovada é obrigatória.");
        this.nomeArquivo = exigir(nomeArquivo, 255, "O nome do arquivo é obrigatório.");
        this.contentType = exigir(contentType, 120, "O tipo do arquivo é obrigatório.");
        if (tamanhoBytes < 1) throw new InvarianteVioladaException("O arquivo assinado está vazio.");
        this.tamanhoBytes = tamanhoBytes;
        this.objectKey = exigir(objectKey, 500, "A chave privada é obrigatória.");
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new InvarianteVioladaException("O hash da cópia assinada é inválido.");
        }
        this.sha256 = sha256;
        this.idUsuarioUpload = Objects.requireNonNull(idUsuarioUpload,
                "Não foi possível identificar quem anexou a cópia.");
        this.enviadaEm = OffsetDateTime.now();
    }

    public boolean pertenceA(UUID reuniaoId, UUID versaoAtaId) {
        return idReuniao.equals(reuniaoId) && idVersaoAta.equals(versaoAtaId);
    }

    private static String exigir(String valor, int maximo, String mensagem) {
        if (valor == null || valor.isBlank()) throw new InvarianteVioladaException(mensagem);
        String texto = valor.trim();
        if (texto.length() > maximo) throw new InvarianteVioladaException(mensagem);
        return texto;
    }
}
