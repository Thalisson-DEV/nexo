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

/** Revisão privada e imutável do DOCX usado como base de um modelo de ata. */
@Entity
@Table(name = "arquivos_modelo_ata_sst")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArquivoModeloAtaSst extends BaseEntity {
    public static final String DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Column(name = "id_modelo", nullable = false, updatable = false)
    private UUID idModelo;

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
    @Column(name = "mapeamento_slots", columnDefinition = "text")
    private String mapeamentoSlots;

    @Column(name = "validado_em")
    private OffsetDateTime validadoEm;

    @Column(name = "id_usuario_validacao")
    private UUID idUsuarioValidacao;

    public ArquivoModeloAtaSst(UUID idModelo, String nomeArquivo, long tamanhoBytes,
                               String objectKey, String sha256, String mapeamentoSlots) {
        this.idModelo = Objects.requireNonNull(idModelo, "O modelo de ata é obrigatório.");
        this.nomeArquivo = texto(nomeArquivo, 255, "O nome do DOCX é obrigatório.");
        if (tamanhoBytes < 1) throw new InvarianteVioladaException("O DOCX está vazio.");
        this.tamanhoBytes = tamanhoBytes;
        this.objectKey = texto(objectKey, 500, "A chave privada do DOCX é obrigatória.");
        this.sha256 = hash(sha256);
        this.contentType = DOCX;
        this.mapeamentoSlots = opcional(mapeamentoSlots, 20_000);
    }

    public void validar(UUID ator) {
        this.idUsuarioValidacao = Objects.requireNonNull(ator,
                "Não foi possível identificar quem validou a base DOCX.");
        this.validadoEm = OffsetDateTime.now();
    }

    public boolean validado() { return validadoEm != null; }

    private static String hash(String valor) {
        if (valor == null || !valor.matches("[0-9a-f]{64}")) {
            throw new InvarianteVioladaException("O hash da base DOCX é inválido.");
        }
        return valor;
    }

    private static String texto(String valor, int limite, String mensagem) {
        if (valor == null || valor.isBlank()) throw new InvarianteVioladaException(mensagem);
        String limpo = valor.trim();
        if (limpo.length() > limite) throw new InvarianteVioladaException(mensagem);
        return limpo;
    }

    private static String opcional(String valor, int limite) {
        return valor == null || valor.isBlank() ? null : texto(valor, limite, "O mapeamento é inválido.");
    }
}
