package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Arquivo do turno: foto do relatório diário ou, com {@code idApr}, documento da APR.
 */
@Entity
@Table(name = "turno_anexos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnexoTurno extends BaseEntity {

    @Column(name = "id_turno", nullable = false, updatable = false)
    private UUID idTurno;

    @Column(name = "id_apr", updatable = false)
    private UUID idApr;

    @Column(name = "nome_arquivo", nullable = false, updatable = false, length = 255)
    private String nomeArquivo;

    @Column(name = "content_type", nullable = false, updatable = false, length = 100)
    private String contentType;

    @Column(name = "tamanho_bytes", nullable = false, updatable = false)
    private long tamanhoBytes;

    @Column(name = "chave_storage", nullable = false, updatable = false, length = 500)
    private String chaveStorage;

    public AnexoTurno(UUID idTurno, UUID idApr, String nomeArquivo, String contentType, long tamanhoBytes, String chaveStorage) {
        this.idTurno = idTurno;
        this.idApr = idApr;
        this.nomeArquivo = nomeArquivo;
        this.contentType = contentType;
        this.tamanhoBytes = tamanhoBytes;
        this.chaveStorage = chaveStorage;
    }
}
