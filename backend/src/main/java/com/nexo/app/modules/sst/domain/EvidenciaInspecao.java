package com.nexo.app.modules.sst.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.UUID;

/**
 * Fotografia que comprova a factualidade de uma {@link InspecaoSeguranca} — a prova de
 * que o inspetor esteve de fato em campo. Toda inspeção lançada carrega ao menos uma.
 *
 * <p>O binário vai para o R2 antes do lançamento da inspeção, o que permite ao PWA subir
 * cada foto isoladamente (com retentativa própria) em conectividade ruim. Por isso a
 * evidência nasce <b>pendente</b>, sem inspeção, e só é vinculada quando a inspeção é
 * registrada. Enquanto pendente, pertence apenas a quem a enviou; se nunca for usada, o
 * expurgo a recolhe.</p>
 */
@Entity
@Table(name = "evidencias_inspecao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvidenciaInspecao extends BaseEntity {

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_inspecao")
    private InspecaoSeguranca inspecao;

    @Column(name = "id_usuario_envio", nullable = false, updatable = false)
    private UUID idUsuarioEnvio;

    @Column(name = "nome_arquivo", nullable = false, length = 255)
    private String nomeArquivo;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "tamanho_bytes", nullable = false)
    private long tamanhoBytes;

    @Column(name = "object_key", nullable = false, length = 1024)
    private String objectKey;

    public EvidenciaInspecao(UUID idUsuarioEnvio, String nomeArquivo, String contentType,
                             long tamanhoBytes, String objectKey) {
        this.idUsuarioEnvio = Objects.requireNonNull(idUsuarioEnvio, "O usuário que enviou a evidência é obrigatório");
        this.nomeArquivo = exigir(nomeArquivo, "O nome do arquivo é obrigatório.");
        this.contentType = exigir(contentType, "O content type da evidência é obrigatório.");
        this.objectKey = exigir(objectKey, "A chave do objeto é obrigatória.");
        if (tamanhoBytes <= 0) {
            throw new InvarianteVioladaException("O tamanho da evidência deve ser maior que zero.");
        }
        if (TipoAnexo.deContentType(contentType) != TipoAnexo.FOTO) {
            throw new InvarianteVioladaException("A evidência da inspeção deve ser uma fotografia.");
        }
        this.tamanhoBytes = tamanhoBytes;
    }

    /**
     * Prende a evidência a uma inspeção. Uma foto só comprova uma inspeção: reaproveitá-la
     * em outra transformaria a evidência em formalidade.
     */
    void vincular(InspecaoSeguranca inspecao) {
        if (this.inspecao != null && (this.inspecao.getId() == null || !this.inspecao.getId().equals(inspecao.getId()))) {
            throw new InvarianteVioladaException("A evidência " + getId() + " já foi utilizada em outra inspeção.");
        }
        this.inspecao = Objects.requireNonNull(inspecao, "Inspeção é obrigatória");
    }

    void desvincular() {
        this.inspecao = null;
    }

    /** Indica se a foto já pertence a alguma inspeção — pendentes ainda podem ser usadas. */
    public boolean estaVinculada() {
        return this.inspecao != null;
    }

    public boolean foiEnviadaPor(UUID idUsuario) {
        return this.idUsuarioEnvio.equals(idUsuario);
    }

    private static String exigir(String valor, String mensagem) {
        if (valor == null || valor.isBlank()) {
            throw new InvarianteVioladaException(mensagem);
        }
        return valor;
    }
}
