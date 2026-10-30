package com.nexo.app.modules.assistente.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Resultado tabular registrado numa conversa (r1, r2...). Toda consulta de dados, calculo ou leitura de
 * planilha vira um resultado imutavel, reutilizado por graficos, dashboards, relatorios e comparacoes
 * posteriores sem nova consulta e sem que o modelo precise copiar numeros.
 */
@Entity
@Table(name = "assistente_resultado",
        uniqueConstraints = @UniqueConstraint(name = "uk_assistente_resultado_codigo",
                columnNames = {"conversa_id", "codigo"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResultadoAssistente {

    /** Procedencia dos dados, exibida ao usuario para diferenciar dado interno de externo. */
    public enum Natureza { INTERNO, EXTERNO, ARQUIVO, CALCULO }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "conversa_id", nullable = false, updatable = false)
    private UUID conversaId;

    @Column(name = "mensagem_id", updatable = false)
    private UUID mensagemId;

    @Column(name = "codigo", nullable = false, length = 20, updatable = false)
    private String codigo;

    @Column(name = "origem", nullable = false, length = 120, updatable = false)
    private String origem;

    @Enumerated(EnumType.STRING)
    @Column(name = "natureza", nullable = false, length = 20, updatable = false)
    private Natureza natureza;

    @Column(name = "titulo", nullable = false, length = 300, updatable = false)
    private String titulo;

    @Column(name = "colunas_json", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String colunasJson;

    @Column(name = "linhas_json", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String linhasJson;

    @Column(name = "total_linhas", nullable = false, updatable = false)
    private int totalLinhas;

    @Column(name = "truncado", nullable = false, updatable = false)
    private boolean truncado;

    @Column(name = "consulta_json", columnDefinition = "TEXT", updatable = false)
    private String consultaJson;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private OffsetDateTime dataCriacao;

    public ResultadoAssistente(UUID conversaId, UUID mensagemId, String codigo, String origem, Natureza natureza,
            String titulo, String colunasJson, String linhasJson, int totalLinhas, boolean truncado,
            String consultaJson) {
        this.conversaId = Objects.requireNonNull(conversaId, "Conversa e obrigatoria");
        this.mensagemId = mensagemId;
        this.codigo = Objects.requireNonNull(codigo, "Codigo e obrigatorio");
        this.origem = Objects.requireNonNull(origem, "Origem e obrigatoria");
        this.natureza = Objects.requireNonNull(natureza, "Natureza e obrigatoria");
        this.titulo = limitar(titulo == null || titulo.isBlank() ? codigo : titulo, 300);
        this.colunasJson = Objects.requireNonNull(colunasJson, "Colunas sao obrigatorias");
        this.linhasJson = Objects.requireNonNull(linhasJson, "Linhas sao obrigatorias");
        this.totalLinhas = totalLinhas;
        this.truncado = truncado;
        this.consultaJson = consultaJson;
        this.dataCriacao = OffsetDateTime.now();
    }

    private static String limitar(String value, int max) {
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max - 3) + "...";
    }
}
