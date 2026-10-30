package com.nexo.app.modules.assistente.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Auditoria imutavel de cada chamada de ferramenta do agente (inclui o SQL analitico executado, o codigo
 * Python enviado ao sandbox e as consultas web). Nunca guarda segredos nem dados pessoais do resultado.
 */
@Entity
@Table(name = "assistente_execucao_ferramenta")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExecucaoFerramentaAssistente {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "mensagem_id", nullable = false, updatable = false)
    private UUID mensagemId;

    @Column(name = "conversa_id", updatable = false)
    private UUID conversaId;

    @Column(name = "usuario_id", updatable = false)
    private UUID usuarioId;

    @Column(name = "ferramenta", length = 120, updatable = false)
    private String ferramenta;

    @Column(name = "tool_call_id", length = 160, updatable = false)
    private String toolCallId;

    @Column(name = "ciclo", updatable = false)
    private Integer ciclo;

    @Column(name = "risco", length = 20, updatable = false)
    private String risco;

    @Column(name = "dataset", length = 120, updatable = false)
    private String dataset;

    @Column(name = "dataset_versao", length = 30, updatable = false)
    private String datasetVersao;

    @Column(name = "argumentos_json", columnDefinition = "TEXT", updatable = false)
    private String argumentosJson;

    @Column(name = "sucesso", nullable = false, updatable = false)
    private boolean sucesso;

    @Column(name = "duracao_ms", nullable = false, updatable = false)
    private long duracaoMs;

    @Column(name = "resumo", length = 1000, updatable = false)
    private String resumo;

    @Column(name = "erro", length = 1000, updatable = false)
    private String erro;

    @Column(name = "resultado_codigo", length = 20, updatable = false)
    private String resultadoCodigo;

    @Column(name = "linhas", updatable = false)
    private Integer linhas;

    @Column(name = "evidencia_json", columnDefinition = "TEXT", updatable = false)
    private String evidenciaJson;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private OffsetDateTime dataCriacao;

    public ExecucaoFerramentaAssistente(UUID mensagemId, UUID conversaId, UUID usuarioId, String ferramenta,
            String toolCallId, Integer ciclo, String risco, String argumentosJson, boolean sucesso, long duracaoMs,
            String resumo, String erro, String resultadoCodigo, Integer linhas) {
        this.mensagemId = mensagemId;
        this.conversaId = conversaId;
        this.usuarioId = usuarioId;
        this.ferramenta = ferramenta;
        this.toolCallId = limitar(toolCallId, 160);
        this.ciclo = ciclo;
        this.risco = risco;
        this.dataset = ferramenta;
        this.argumentosJson = argumentosJson;
        this.sucesso = sucesso;
        this.duracaoMs = duracaoMs;
        this.resumo = limitar(resumo, 1000);
        this.erro = limitar(erro, 1000);
        this.resultadoCodigo = resultadoCodigo;
        this.linhas = linhas;
        this.dataCriacao = OffsetDateTime.now();
    }

    private static String limitar(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}
