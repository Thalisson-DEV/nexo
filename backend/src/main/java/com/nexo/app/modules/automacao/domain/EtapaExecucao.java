package com.nexo.app.modules.automacao.domain;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** O registro de um passo numa execução (um por nó e iteração). É o ponto de retomada. */
@Entity
@Table(name = "automacao_etapas")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EtapaExecucao {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "id_execucao", nullable = false, updatable = false)
    private UUID idExecucao;

    @Column(name = "no_id", nullable = false, length = 40, updatable = false)
    private String noId;

    @Column(name = "iteracao", nullable = false, length = 400, updatable = false)
    private String iteracao;

    @Column(name = "tipo", nullable = false, length = 64)
    private String tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusEtapa status;

    @Column(name = "tentativa", nullable = false)
    private int tentativa;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "entradas", columnDefinition = "jsonb")
    private Map<String, Object> entradas;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "saidas", columnDefinition = "jsonb")
    private Map<String, Object> saidas;

    @Column(name = "erro", length = 2000)
    private String erro;

    @Column(name = "inicio")
    private OffsetDateTime inicio;

    @Column(name = "fim")
    private OffsetDateTime fim;

    @Column(name = "proxima_em")
    private OffsetDateTime proximaEm;

    public EtapaExecucao(UUID idExecucao, String noId, String iteracao) {
        this.id = UUID.randomUUID();
        this.idExecucao = idExecucao;
        this.noId = noId;
        this.iteracao = iteracao == null ? "" : iteracao;
    }

    /** Copia o estado que o interpretador acabou de gravar. */
    public void atualizar(String tipo, StatusEtapa status, int tentativa, Map<String, Object> entradas,
                          Map<String, Object> saidas, String erro, OffsetDateTime inicio, OffsetDateTime fim,
                          OffsetDateTime proximaEm) {
        this.tipo = tipo;
        this.status = status;
        this.tentativa = tentativa;
        this.entradas = entradas;
        this.saidas = saidas;
        this.erro = erro;
        this.inicio = inicio;
        this.fim = fim;
        this.proximaEm = proximaEm;
    }
}
