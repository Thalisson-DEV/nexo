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

/** DOCX determinístico pronto para impressão, distinto da cópia assinada. */
@Entity
@Table(name = "arquivos_ata_gerada_sst")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArquivoAtaGeradaSst extends BaseEntity {
    @Column(name = "id_reuniao", nullable = false, updatable = false)
    private UUID idReuniao;
    @Column(name = "id_versao_ata", nullable = false, updatable = false)
    private UUID idVersaoAta;
    @Column(name = "id_modelo_ata", nullable = false, updatable = false)
    private UUID idModeloAta;
    @Column(name = "revisao_modelo_ata", nullable = false, updatable = false)
    private int revisaoModeloAta;
    @Column(name = "hash_modelo_docx", nullable = false, updatable = false, length = 64)
    private String hashModeloDocx;
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
    @Column(name = "id_usuario_geracao", nullable = false, updatable = false)
    private UUID idUsuarioGeracao;
    @Column(name = "gerado_em", nullable = false, updatable = false)
    private OffsetDateTime geradoEm;

    public ArquivoAtaGeradaSst(UUID idReuniao, UUID idVersaoAta, UUID idModeloAta,
                               int revisaoModeloAta, String hashModeloDocx, String nomeArquivo,
                               long tamanhoBytes, String objectKey, String sha256, UUID ator) {
        this.idReuniao = Objects.requireNonNull(idReuniao, "A reunião é obrigatória.");
        this.idVersaoAta = Objects.requireNonNull(idVersaoAta, "A versão da ata é obrigatória.");
        this.idModeloAta = Objects.requireNonNull(idModeloAta, "O modelo é obrigatório.");
        if (revisaoModeloAta < 1) throw new InvarianteVioladaException("A revisão do modelo é inválida.");
        this.revisaoModeloAta = revisaoModeloAta;
        this.hashModeloDocx = hash(hashModeloDocx);
        this.nomeArquivo = texto(nomeArquivo, 255, "O nome do DOCX gerado é obrigatório.");
        if (tamanhoBytes < 1) throw new InvarianteVioladaException("O DOCX gerado está vazio.");
        this.tamanhoBytes = tamanhoBytes;
        this.objectKey = texto(objectKey, 500, "A chave privada é obrigatória.");
        this.sha256 = hash(sha256);
        this.contentType = ArquivoModeloAtaSst.DOCX;
        this.idUsuarioGeracao = Objects.requireNonNull(ator, "O gerador é obrigatório.");
        this.geradoEm = OffsetDateTime.now();
    }

    private static String hash(String valor) {
        if (valor == null || !valor.matches("[0-9a-f]{64}")) {
            throw new InvarianteVioladaException("O hash do DOCX é inválido.");
        }
        return valor;
    }

    private static String texto(String valor, int limite, String mensagem) {
        if (valor == null || valor.isBlank()) throw new InvarianteVioladaException(mensagem);
        String limpo = valor.trim();
        if (limpo.length() > limite) throw new InvarianteVioladaException(mensagem);
        return limpo;
    }
}
