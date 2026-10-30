package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.AuditIgnore;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/** Revisão autônoma de modelo. Publicar torna toda a estrutura imutável. */
@Entity
@Table(name = "modelos_ata_sst")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ModeloAtaSst extends BaseEntity {

    @Column(name = "familia_id", nullable = false, updatable = false)
    private UUID familiaId;

    @Column(name = "revisao", nullable = false, updatable = false)
    private int revisao;

    @Column(name = "id_revisao_anterior", updatable = false)
    private UUID idRevisaoAnterior;

    @Column(name = "id_modelo_duplicado", updatable = false)
    private UUID idModeloDuplicado;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_corpo", nullable = false, updatable = false, length = 20)
    private TipoCorpoGovernancaSst tipoCorpo;

    @Column(name = "id_base_aplicavel", updatable = false)
    private UUID idBaseAplicavel;

    @Column(name = "nome", nullable = false, length = 160)
    private String nome;

    @AuditIgnore
    @Column(name = "orientacoes_gerais", length = 2000)
    private String orientacoesGerais;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusModeloAtaSst status;

    @Column(name = "modelo_inicial", nullable = false, updatable = false)
    private boolean modeloInicial;

    @Column(name = "publicado_em")
    private OffsetDateTime publicadoEm;

    @Column(name = "id_usuario_publicacao")
    private UUID idUsuarioPublicacao;

    @Column(name = "retirado_em")
    private OffsetDateTime retiradoEm;

    @Column(name = "id_usuario_retirada")
    private UUID idUsuarioRetirada;

    @Column(name = "id_arquivo_docx")
    private UUID idArquivoDocx;

    @Column(name = "hash_arquivo_docx", length = 64)
    private String hashArquivoDocx;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_arquivo_docx", insertable = false, updatable = false)
    private ArquivoModeloAtaSst arquivoDocxDetails;

    public ModeloAtaSst(String nome, TipoCorpoGovernancaSst tipoCorpo, UUID idBaseAplicavel,
                        String orientacoesGerais) {
        this.familiaId = UUID.randomUUID();
        this.revisao = 1;
        this.tipoCorpo = Objects.requireNonNull(tipoCorpo, "O tipo do corpo é obrigatório.");
        this.idBaseAplicavel = idBaseAplicavel;
        this.status = StatusModeloAtaSst.RASCUNHO;
        this.modeloInicial = false;
        atualizarRascunho(nome, orientacoesGerais);
    }

    private ModeloAtaSst(ModeloAtaSst origem, boolean novaRevisao) {
        this.familiaId = novaRevisao ? origem.familiaId : UUID.randomUUID();
        this.revisao = novaRevisao ? origem.revisao + 1 : 1;
        this.idRevisaoAnterior = novaRevisao ? origem.getId() : null;
        this.idModeloDuplicado = novaRevisao ? null : origem.getId();
        this.tipoCorpo = origem.tipoCorpo;
        this.idBaseAplicavel = origem.idBaseAplicavel;
        this.nome = novaRevisao ? origem.nome : origem.nome + " (cópia)";
        this.orientacoesGerais = origem.orientacoesGerais;
        this.status = StatusModeloAtaSst.RASCUNHO;
        this.modeloInicial = false;
        this.idArquivoDocx = origem.idArquivoDocx;
        this.hashArquivoDocx = origem.hashArquivoDocx;
    }

    public void atualizarRascunho(String nome, String orientacoesGerais) {
        exigirRascunho("editar");
        this.nome = sanitizar(nome, 160, "Informe o nome do modelo.");
        this.orientacoesGerais = sanitizarOpcional(orientacoesGerais, 2000);
    }

    public ModeloAtaSst criarNovaRevisao() {
        if (status == StatusModeloAtaSst.RASCUNHO) {
            throw new InvarianteVioladaException("Edite o rascunho atual; nova revisão exige modelo publicado ou retirado.");
        }
        return new ModeloAtaSst(this, true);
    }

    public ModeloAtaSst duplicar() {
        return new ModeloAtaSst(this, false);
    }

    public void publicar(UUID idUsuario) {
        exigirRascunho("publicar");
        if (idArquivoDocx == null || hashArquivoDocx == null) {
            throw new InvarianteVioladaException(
                    "Envie e valide a base DOCX antes de publicar o modelo.");
        }
        this.idUsuarioPublicacao = Objects.requireNonNull(idUsuario,
                "Não foi possível identificar quem publicou o modelo.");
        this.publicadoEm = OffsetDateTime.now();
        this.status = StatusModeloAtaSst.PUBLICADO;
    }

    public void retirar(UUID idUsuario) {
        if (status != StatusModeloAtaSst.PUBLICADO) {
            throw new InvarianteVioladaException("Somente um modelo publicado pode ser retirado.");
        }
        this.idUsuarioRetirada = Objects.requireNonNull(idUsuario,
                "Não foi possível identificar quem retirou o modelo.");
        this.retiradoEm = OffsetDateTime.now();
        this.status = StatusModeloAtaSst.RETIRADO;
    }

    public boolean aplicavelA(TipoCorpoGovernancaSst tipo, UUID idBase) {
        return status == StatusModeloAtaSst.PUBLICADO && tipoCorpo == tipo
                && (idBaseAplicavel == null || idBaseAplicavel.equals(idBase));
    }

    public void vincularArquivoDocx(UUID idArquivo, String hash) {
        exigirRascunho("alterar a base DOCX");
        this.idArquivoDocx = Objects.requireNonNull(idArquivo, "A base DOCX é obrigatória.");
        if (hash == null || !hash.matches("[0-9a-f]{64}")) {
            throw new InvarianteVioladaException("O hash da base DOCX é inválido.");
        }
        this.hashArquivoDocx = hash;
    }

    public String criarSnapshot() {
        if (status != StatusModeloAtaSst.PUBLICADO) {
            throw new InvarianteVioladaException("Selecione uma revisão publicada do modelo.");
        }
        StringBuilder s = new StringBuilder();
        s.append("MODELO ").append(nome).append(" | revisão ").append(revisao).append('\n');
        if (orientacoesGerais != null) s.append("ORIENTAÇÕES: ").append(orientacoesGerais).append('\n');
        return s.toString().trim();
    }

    private void exigirRascunho(String acao) {
        if (status != null && status != StatusModeloAtaSst.RASCUNHO) {
            throw new InvarianteVioladaException(
                    "Uma revisão publicada é imutável. Crie nova revisão para " + acao + ".");
        }
    }

    static String sanitizar(String valor, int limite, String mensagem) {
        if (valor == null || valor.isBlank()) throw new InvarianteVioladaException(mensagem);
        String texto = valor.strip().replace("\u0000", "");
        if (texto.length() > limite) throw new InvarianteVioladaException(mensagem);
        return texto;
    }

    static String sanitizarOpcional(String valor, int limite) {
        return valor == null || valor.isBlank() ? null : sanitizar(valor, limite, "A orientação excede o limite permitido.");
    }
}
