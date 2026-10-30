package com.nexo.app.modules.assistente.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.UUID;

/** Conteudo binario de um arquivo do Copiloto quando o armazenamento configurado e o banco. */
@Entity
@Table(name = "assistente_arquivo_conteudo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArquivoConteudo {

    @Id
    @Column(name = "arquivo_id", nullable = false, updatable = false)
    private UUID arquivoId;

    @Column(name = "conteudo", nullable = false, length = 64 * 1024 * 1024)
    private byte[] conteudo;

    public ArquivoConteudo(UUID arquivoId, byte[] conteudo) {
        this.arquivoId = Objects.requireNonNull(arquivoId, "Arquivo e obrigatorio");
        this.conteudo = Objects.requireNonNull(conteudo, "Conteudo e obrigatorio");
    }
}
