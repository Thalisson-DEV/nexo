package com.nexo.app.modules.assistente.domain;

import com.nexo.app.shared.domain.AuditIgnore;
import com.nexo.app.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.UUID;

/**
 * Artefato produzido pelo agente (dashboard, grafico, tabela, relatorio, documento). A especificacao e
 * declarativa e ja traz os dados resolvidos a partir dos resultados da conversa; o frontend renderiza
 * apenas componentes confiaveis do Nexo.
 */
@Entity
@Table(name = "assistente_artefato",
        uniqueConstraints = @UniqueConstraint(name = "uk_assistente_artefato_codigo",
                columnNames = {"conversa_id", "codigo"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArtefatoAssistente extends BaseEntity {

    public enum Tipo { DASHBOARD, GRAFICO, TABELA, RELATORIO, DOCUMENTO, PLANILHA, IMAGEM, CODIGO }

    @Column(name = "conversa_id", nullable = false, updatable = false)
    private UUID conversaId;

    @Column(name = "mensagem_id")
    private UUID mensagemId;

    @Column(name = "codigo", nullable = false, length = 20, updatable = false)
    private String codigo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private Tipo tipo;

    @Column(name = "titulo", nullable = false, length = 300)
    private String titulo;

    @Column(name = "descricao", length = 1000)
    private String descricao;

    @AuditIgnore
    @Column(name = "especificacao_json", nullable = false, columnDefinition = "TEXT")
    private String especificacaoJson;

    @Column(name = "arquivo_id")
    private UUID arquivoId;

    public ArtefatoAssistente(UUID conversaId, UUID mensagemId, String codigo, Tipo tipo, String titulo,
            String descricao, String especificacaoJson, UUID arquivoId) {
        this.conversaId = Objects.requireNonNull(conversaId, "Conversa e obrigatoria");
        this.mensagemId = mensagemId;
        this.codigo = Objects.requireNonNull(codigo, "Codigo e obrigatorio");
        this.tipo = Objects.requireNonNull(tipo, "Tipo e obrigatorio");
        this.titulo = limitar(Objects.requireNonNull(titulo, "Titulo e obrigatorio"), 300);
        this.descricao = descricao == null ? null : limitar(descricao, 1000);
        this.especificacaoJson = Objects.requireNonNull(especificacaoJson, "Especificacao e obrigatoria");
        this.arquivoId = arquivoId;
    }

    private static String limitar(String value, int max) {
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max - 3) + "...";
    }
}
