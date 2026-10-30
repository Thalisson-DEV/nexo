package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.AuditIgnore;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.persistence.CryptoConverterImpl;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/** Evidência operacional de disponibilização da ata ou de encaminhamentos da CIPA. */
@Entity
@Table(name = "comunicacoes_ata_cipa_sst")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComunicacaoAtaCipaSst extends BaseEntity {

    @Column(name = "id_reuniao", nullable = false, updatable = false)
    private UUID idReuniao;

    @Column(name = "id_versao_ata", nullable = false, updatable = false)
    private UUID idVersaoAta;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, updatable = false, length = 60)
    private TipoComunicacaoAtaCipaSst tipo;

    @Column(name = "comunicada_em", nullable = false, updatable = false)
    private OffsetDateTime comunicadaEm;

    @AuditIgnore
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "canal", nullable = false, updatable = false, length = 1024)
    private String canal;

    @AuditIgnore
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "alcance", updatable = false, length = 2048)
    private String alcance;

    @Column(name = "id_responsavel", nullable = false, updatable = false)
    private UUID idResponsavel;

    @AuditIgnore
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "nome_evidencia", updatable = false, length = 1024)
    private String nomeEvidencia;

    @Column(name = "content_type_evidencia", updatable = false, length = 120)
    private String contentTypeEvidencia;

    @Column(name = "tamanho_evidencia", updatable = false)
    private Long tamanhoEvidencia;

    @AuditIgnore
    @Column(name = "object_key_evidencia", updatable = false, length = 500)
    private String objectKeyEvidencia;

    @Column(name = "sha256_evidencia", updatable = false, length = 64)
    private String sha256Evidencia;

    public ComunicacaoAtaCipaSst(UUID idReuniao, UUID idVersaoAta,
                                  TipoComunicacaoAtaCipaSst tipo, OffsetDateTime comunicadaEm,
                                  String canal, String alcance, UUID idResponsavel,
                                  String nomeEvidencia, String contentTypeEvidencia,
                                  Long tamanhoEvidencia, String objectKeyEvidencia,
                                  String sha256Evidencia) {
        this.idReuniao = Objects.requireNonNull(idReuniao, "A reunião é obrigatória.");
        this.idVersaoAta = Objects.requireNonNull(idVersaoAta, "A ata aprovada é obrigatória.");
        this.tipo = Objects.requireNonNull(tipo, "O tipo da comunicação é obrigatório.");
        this.comunicadaEm = Objects.requireNonNull(comunicadaEm, "A data da comunicação é obrigatória.");
        this.canal = exigir(canal, 255, "Informe o canal utilizado.");
        this.alcance = opcional(alcance, 500);
        if (tipo == TipoComunicacaoAtaCipaSst.ENCAMINHAMENTOS_AOS_TRABALHADORES
                && this.alcance == null) {
            throw new InvarianteVioladaException("Informe o alcance da comunicação aos trabalhadores.");
        }
        this.idResponsavel = Objects.requireNonNull(idResponsavel,
                "Não foi possível identificar o responsável pela comunicação.");
        validarEvidencia(nomeEvidencia, contentTypeEvidencia, tamanhoEvidencia,
                objectKeyEvidencia, sha256Evidencia);
    }

    public boolean possuiEvidencia() {
        return objectKeyEvidencia != null;
    }

    public boolean pertenceA(UUID reuniaoId) {
        return idReuniao.equals(reuniaoId);
    }

    private void validarEvidencia(String nome, String tipoArquivo, Long tamanho,
                                   String chave, String hash) {
        boolean algum = nome != null || tipoArquivo != null || tamanho != null || chave != null || hash != null;
        if (!algum) return;
        if (nome == null || tipoArquivo == null || tamanho == null || tamanho < 1 || chave == null
                || hash == null || !hash.matches("[0-9a-f]{64}")) {
            throw new InvarianteVioladaException("Os metadados da evidência da comunicação estão incompletos.");
        }
        this.nomeEvidencia = exigir(nome, 255, "O nome da evidência é inválido.");
        this.contentTypeEvidencia = exigir(tipoArquivo, 120, "O tipo da evidência é inválido.");
        this.tamanhoEvidencia = tamanho;
        this.objectKeyEvidencia = exigir(chave, 500, "A chave privada da evidência é inválida.");
        this.sha256Evidencia = hash;
    }

    private static String exigir(String valor, int maximo, String mensagem) {
        if (valor == null || valor.isBlank()) throw new InvarianteVioladaException(mensagem);
        String texto = valor.trim();
        if (texto.length() > maximo) throw new InvarianteVioladaException(mensagem);
        return texto;
    }

    private static String opcional(String valor, int maximo) {
        return valor == null || valor.isBlank() ? null : exigir(valor, maximo, "O alcance é inválido.");
    }
}
