package com.nexo.app.modules.assistente.domain;

import com.nexo.app.shared.domain.AuditIgnore;
import com.nexo.app.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Conversa do Copiloto pertencente a um unico usuario. O titulo nasce provisorio (primeira pergunta),
 * e substituido por um titulo gerado pelo modelo e nunca sobrescreve um nome dado pelo proprio usuario.
 */
@Entity
@Table(name = "assistente_conversa")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConversaAssistente extends BaseEntity {

    public static final String TITULO_PADRAO = "Nova conversa";
    public static final int TITULO_MAXIMO = 160;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Column(name = "titulo", nullable = false, length = TITULO_MAXIMO)
    private String titulo;

    @Column(name = "ultima_atividade_em", nullable = false)
    private OffsetDateTime ultimaAtividadeEm;

    @AuditIgnore
    @Column(name = "contexto_json", columnDefinition = "TEXT")
    private String contextoJson;

    @Column(name = "titulo_definido_pelo_usuario", nullable = false)
    private boolean tituloDefinidoPeloUsuario;

    /** Superficie onde a conversa comecou: workspace completo ou um painel (dashboard). */
    @Enumerated(EnumType.STRING)
    @Column(name = "origem", nullable = false, length = 20)
    private OrigemConversa origem = OrigemConversa.WORKSPACE;

    /** Identificador do painel quando {@code origem = DASHBOARD}. */
    @Column(name = "origem_id", length = 80)
    private String origemId;

    public ConversaAssistente(UUID usuarioId, String titulo) {
        this(usuarioId, titulo, null);
    }

    public ConversaAssistente(UUID usuarioId, String titulo, String contextoJson) {
        this.usuarioId = Objects.requireNonNull(usuarioId, "Usuario e obrigatorio");
        this.titulo = normalizeTitle(titulo);
        this.tituloDefinidoPeloUsuario = titulo != null && !titulo.isBlank();
        this.contextoJson = contextoJson == null || contextoJson.isBlank() ? null : contextoJson;
        this.ultimaAtividadeEm = OffsetDateTime.now();
    }

    /** Conversa tecnica de uma conexao de IA externa: guarda resultados e auditoria das chamadas. */
    public static ConversaAssistente deConexao(UUID usuarioId, String nomeConexao) {
        ConversaAssistente conversa = new ConversaAssistente(usuarioId, "Conexao: " + nomeConexao);
        conversa.origem = OrigemConversa.MCP;
        return conversa;
    }

    /** Associa a conversa ao painel de origem; so vale enquanto a conversa ainda nao tem origem de painel. */
    public void associarPainel(String painelId) {
        if (painelId == null || painelId.isBlank() || origem == OrigemConversa.DASHBOARD) return;
        this.origem = OrigemConversa.DASHBOARD;
        String limpo = painelId.trim();
        this.origemId = limpo.length() <= 80 ? limpo : limpo.substring(0, 80);
    }

    /** Titulo provisorio enquanto o titulo gerado nao chega. */
    public void definirTituloPelaPrimeiraPergunta(String pergunta) {
        if (tituloDefinidoPeloUsuario || !TITULO_PADRAO.equals(this.titulo)
                || pergunta == null || pergunta.isBlank()) {
            return;
        }
        this.titulo = normalizeTitle(pergunta.replaceAll("\\s+", " "));
    }

    /** Titulo sugerido pelo modelo; nunca sobrescreve um nome escolhido pelo usuario. */
    public boolean definirTituloGerado(String sugestao) {
        if (tituloDefinidoPeloUsuario || sugestao == null || sugestao.isBlank()) return false;
        this.titulo = normalizeTitle(sugestao);
        return true;
    }

    public void renomear(String novoTitulo) {
        if (novoTitulo == null || novoTitulo.isBlank()) {
            throw new IllegalArgumentException("Informe um titulo para a conversa");
        }
        this.titulo = normalizeTitle(novoTitulo);
        this.tituloDefinidoPeloUsuario = true;
    }

    public void registrarAtividade() {
        this.ultimaAtividadeEm = OffsetDateTime.now();
    }

    public void definirContexto(String contextoJson) {
        this.contextoJson = contextoJson == null || contextoJson.isBlank() ? null : contextoJson;
    }

    public boolean pertenceA(UUID usuario) {
        return usuarioId.equals(usuario);
    }

    private String normalizeTitle(String value) {
        String normalized = value == null || value.isBlank() ? TITULO_PADRAO : value.trim();
        return normalized.length() <= TITULO_MAXIMO
                ? normalized
                : normalized.substring(0, TITULO_MAXIMO - 3).trim() + "...";
    }
}
