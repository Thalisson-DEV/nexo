package com.nexo.app.modules.automacao.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Arquivo produzido numa execução (ex.: o relatório PDF do Copiloto), anexável a e-mails. */
@Entity
@Table(name = "automacao_arquivos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArquivoAutomacao {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "id_execucao", nullable = false, updatable = false)
    private UUID idExecucao;

    @Column(name = "nome", nullable = false, length = 255, updatable = false)
    private String nome;

    @Column(name = "tipo_conteudo", nullable = false, length = 120, updatable = false)
    private String tipoConteudo;

    @Column(name = "tamanho_bytes", nullable = false, updatable = false)
    private long tamanhoBytes;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "conteudo", nullable = false, updatable = false)
    private byte[] conteudo;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    public ArquivoAutomacao(UUID idExecucao, String nome, String tipoConteudo, byte[] conteudo, OffsetDateTime agora) {
        this.id = UUID.randomUUID();
        this.idExecucao = idExecucao;
        this.nome = nome.length() > 255 ? nome.substring(0, 255) : nome;
        this.tipoConteudo = tipoConteudo == null ? "application/octet-stream" : tipoConteudo;
        this.conteudo = conteudo;
        this.tamanhoBytes = conteudo.length;
        this.criadoEm = agora;
    }
}
