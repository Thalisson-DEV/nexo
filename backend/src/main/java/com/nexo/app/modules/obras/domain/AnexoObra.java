package com.nexo.app.modules.obras.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * Representa um anexo técnico ou documental vinculado a uma {@link Obra}.
 * Os arquivos físicos são armazenados no Cloudflare R2 e referenciados pela chave {@code chaveStorage}.
 */
@Entity
@Table(name = "anexos_obras")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnexoObra extends BaseEntity {

    @NotNull(message = "A obra vinculada é obrigatória")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_obra", nullable = false)
    @JsonBackReference
    private Obra obra;

    @NotNull(message = "O tipo do anexo é obrigatório")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tipo_anexo", nullable = false)
    private TipoAnexoObra tipoAnexo;

    @NotBlank(message = "O nome do arquivo é obrigatório")
    @Column(name = "nome_arquivo", nullable = false)
    private String nomeArquivo;

    @NotBlank(message = "O tipo MIME do arquivo é obrigatório")
    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "tamanho_bytes", nullable = false)
    private long tamanhoBytes;

    @NotBlank(message = "A chave de armazenamento é obrigatória")
    @Column(name = "chave_storage", nullable = false, length = 500)
    private String chaveStorage;

    @Column(name = "observacoes", length = 500)
    private String observacoes;

    public AnexoObra(Obra obra, TipoAnexoObra tipoAnexo, String nomeArquivo,
                     String contentType, long tamanhoBytes, String chaveStorage, String observacoes) {
        this.obra = Objects.requireNonNull(obra, "A obra vinculada é obrigatória.");
        this.tipoAnexo = Objects.requireNonNull(tipoAnexo, "O tipo do anexo é obrigatório.");
        if (nomeArquivo == null || nomeArquivo.isBlank()) {
            throw new InvarianteVioladaException("O nome do arquivo é obrigatório.");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new InvarianteVioladaException("O tipo MIME do arquivo é obrigatório.");
        }
        if (tamanhoBytes <= 0) {
            throw new InvarianteVioladaException("O tamanho do arquivo deve ser maior que zero.");
        }
        if (chaveStorage == null || chaveStorage.isBlank()) {
            throw new InvarianteVioladaException("A chave de armazenamento é obrigatória.");
        }
        this.nomeArquivo = nomeArquivo.trim();
        this.contentType = contentType.trim();
        this.tamanhoBytes = tamanhoBytes;
        this.chaveStorage = chaveStorage.trim();
        this.observacoes = observacoes != null && !observacoes.isBlank() ? observacoes.trim() : null;
    }
}
