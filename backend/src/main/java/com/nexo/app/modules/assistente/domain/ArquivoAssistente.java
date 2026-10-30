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
 * Arquivo do Copiloto: anexo enviado pelo usuario ou arquivo gerado pelo agente (relatorio PDF,
 * planilha exportada, grafico do Python...). O conteudo binario fica em {@link ArquivoConteudo}
 * (armazenamento em banco) ou no object storage; aqui ficam metadados, texto extraido e dados tabulares.
 */
@Entity
@Table(name = "assistente_arquivo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArquivoAssistente extends BaseEntity {

    public enum Origem { UPLOAD, GERADO }

    public enum Armazenamento { BANCO, R2 }

    public enum Status { PRONTO, FALHOU }

    public enum Categoria { PLANILHA, CSV, PDF, TEXTO, JSON, IMAGEM, DOCUMENTO, OUTRO }

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Column(name = "conversa_id")
    private UUID conversaId;

    @Column(name = "mensagem_id")
    private UUID mensagemId;

    /** Referencia curta usada pelo agente dentro da conversa (f1, f2...). */
    @Column(name = "codigo", length = 20)
    private String codigo;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", nullable = false, length = 20, updatable = false)
    private Origem origem;

    @Column(name = "nome", nullable = false, length = 255)
    private String nome;

    @Column(name = "tipo_conteudo", nullable = false, length = 160)
    private String tipoConteudo;

    @Column(name = "extensao", length = 20)
    private String extensao;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", nullable = false, length = 30)
    private Categoria categoria;

    @Column(name = "tamanho_bytes", nullable = false)
    private long tamanhoBytes;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "armazenamento", nullable = false, length = 20)
    private Armazenamento armazenamento;

    @Column(name = "chave_armazenamento", length = 300)
    private String chaveArmazenamento;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @AuditIgnore
    @Column(name = "resumo_json", columnDefinition = "TEXT")
    private String resumoJson;

    @AuditIgnore
    @Column(name = "texto_extraido", columnDefinition = "TEXT")
    private String textoExtraido;

    @AuditIgnore
    @Column(name = "dados_json", columnDefinition = "TEXT")
    private String dadosJson;

    @Column(name = "erro", length = 500)
    private String erro;

    public ArquivoAssistente(UUID usuarioId, UUID conversaId, Origem origem, String nome, String tipoConteudo,
            String extensao, Categoria categoria, long tamanhoBytes, String sha256, Armazenamento armazenamento) {
        this.usuarioId = Objects.requireNonNull(usuarioId, "Usuario e obrigatorio");
        this.conversaId = conversaId;
        this.origem = Objects.requireNonNull(origem, "Origem e obrigatoria");
        this.nome = normalizarNome(nome);
        this.tipoConteudo = tipoConteudo == null || tipoConteudo.isBlank() ? "application/octet-stream" : tipoConteudo;
        this.extensao = extensao;
        this.categoria = Objects.requireNonNull(categoria, "Categoria e obrigatoria");
        this.tamanhoBytes = tamanhoBytes;
        this.sha256 = Objects.requireNonNull(sha256, "Hash e obrigatorio");
        this.armazenamento = Objects.requireNonNull(armazenamento, "Armazenamento e obrigatorio");
        this.status = Status.PRONTO;
    }

    public void definirChaveArmazenamento(String chave) {
        this.chaveArmazenamento = chave;
    }

    public void registrarExtracao(String resumoJson, String textoExtraido, String dadosJson) {
        this.resumoJson = resumoJson;
        this.textoExtraido = textoExtraido;
        this.dadosJson = dadosJson;
        this.status = Status.PRONTO;
        this.erro = null;
    }

    public void registrarFalhaExtracao(String erro) {
        this.status = Status.FALHOU;
        this.erro = erro == null ? null : erro.length() <= 500 ? erro : erro.substring(0, 497) + "...";
    }

    /** Vincula um anexo pendente (enviado antes da primeira mensagem) a conversa e a pergunta. */
    public void vincular(UUID conversaId, UUID mensagemId, String codigo) {
        if (this.conversaId != null && !this.conversaId.equals(conversaId)) {
            throw new IllegalStateException("Arquivo ja pertence a outra conversa");
        }
        this.conversaId = Objects.requireNonNull(conversaId, "Conversa e obrigatoria");
        if (mensagemId != null) this.mensagemId = mensagemId;
        if (this.codigo == null) this.codigo = codigo;
    }

    public boolean pertenceA(UUID usuario) {
        return usuarioId.equals(usuario);
    }

    private static String normalizarNome(String value) {
        String base = value == null || value.isBlank() ? "arquivo" : value.trim();
        base = base.replaceAll("[\\x00-\\x1f/\\\\:*?\"<>|]+", "_");
        return base.length() <= 255 ? base : base.substring(0, 255);
    }
}
