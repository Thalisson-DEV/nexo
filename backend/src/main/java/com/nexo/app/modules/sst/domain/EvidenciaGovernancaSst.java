package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.AuditIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "evidencias_governanca_sst")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvidenciaGovernancaSst extends BaseEntity {

    @Column(name = "id_acao")
    private UUID idAcao;

    @Column(name = "id_execucao")
    private UUID idExecucao;

    @Column(name = "id_usuario_envio", nullable = false)
    private UUID idUsuarioEnvio;

    @AuditIgnore
    @Column(name = "nome_arquivo", nullable = false, length = 255)
    private String nomeArquivo;

    @AuditIgnore
    @Column(name = "object_key", nullable = false, length = 700, unique = true)
    private String objectKey;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(name = "tamanho_bytes", nullable = false)
    private long tamanhoBytes;

    @Column(name = "hash_conteudo", nullable = false, length = 64)
    private String hashConteudo;

    private EvidenciaGovernancaSst(UUID idAcao, UUID idExecucao, UUID idUsuarioEnvio,
                                   String nomeArquivo, String objectKey, String contentType,
                                   long tamanhoBytes, String hashConteudo) {
        if ((idAcao == null) == (idExecucao == null)) {
            throw new IllegalArgumentException("A evidência deve pertencer a uma ação ou a um checklist.");
        }
        this.idAcao = idAcao;
        this.idExecucao = idExecucao;
        this.idUsuarioEnvio = Objects.requireNonNull(idUsuarioEnvio, "Usuário é obrigatório");
        this.nomeArquivo = Objects.requireNonNull(nomeArquivo, "Nome é obrigatório");
        this.objectKey = Objects.requireNonNull(objectKey, "Chave é obrigatória");
        this.contentType = Objects.requireNonNull(contentType, "Tipo é obrigatório");
        this.tamanhoBytes = tamanhoBytes;
        this.hashConteudo = Objects.requireNonNull(hashConteudo, "Hash é obrigatório");
    }

    public static EvidenciaGovernancaSst paraAcao(UUID idAcao, UUID usuarioId,
                                                   String nomeArquivo, String objectKey,
                                                   String contentType, long tamanhoBytes,
                                                   String hashConteudo) {
        return new EvidenciaGovernancaSst(Objects.requireNonNull(idAcao), null, usuarioId,
                nomeArquivo, objectKey, contentType, tamanhoBytes, hashConteudo);
    }

    public static EvidenciaGovernancaSst paraChecklist(UUID idExecucao, UUID usuarioId,
                                                        String nomeArquivo, String objectKey,
                                                        String contentType, long tamanhoBytes,
                                                        String hashConteudo) {
        return new EvidenciaGovernancaSst(null, Objects.requireNonNull(idExecucao), usuarioId,
                nomeArquivo, objectKey, contentType, tamanhoBytes, hashConteudo);
    }
}
