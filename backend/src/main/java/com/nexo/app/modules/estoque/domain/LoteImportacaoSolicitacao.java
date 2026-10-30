package com.nexo.app.modules.estoque.domain;

import com.nexo.app.modules.estoque.application.dto.PreviaImportacaoSolicitacaoTecnica;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/** Previa validada mantida no servidor; nunca armazena os bytes do arquivo original. */
@Entity
@Table(name = "lotes_importacao_solicitacao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoteImportacaoSolicitacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "hash_arquivo", nullable = false, length = 64)
    private String hashArquivo;

    @Column(name = "nome_arquivo", nullable = false, length = 255)
    private String nomeArquivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "formato", nullable = false, length = 10)
    private Formato formato;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private Status status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "previa", nullable = false)
    private PreviaImportacaoSolicitacaoTecnica previa;

    @Column(name = "total_linhas", nullable = false)
    private int totalLinhas;

    @Column(name = "total_solicitacoes", nullable = false)
    private int totalSolicitacoes;

    @Column(name = "criado_por", nullable = false)
    private UUID criadoPor;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "expira_em", nullable = false)
    private OffsetDateTime expiraEm;

    @Column(name = "confirmado_por")
    private UUID confirmadoPor;

    @Column(name = "confirmado_em")
    private OffsetDateTime confirmadoEm;

    public LoteImportacaoSolicitacao(
            String tokenHash,
            String hashArquivo,
            String nomeArquivo,
            Formato formato,
            PreviaImportacaoSolicitacaoTecnica previa,
            int totalLinhas,
            UUID criadoPor,
            OffsetDateTime criadoEm,
            OffsetDateTime expiraEm
    ) {
        this.tokenHash = exigirHash(tokenHash, "O hash do token e obrigatorio");
        this.hashArquivo = exigirHash(hashArquivo, "O hash do arquivo e obrigatorio");
        this.nomeArquivo = exigirTexto(nomeArquivo, "O nome do arquivo e obrigatorio");
        this.formato = Objects.requireNonNull(formato, "O formato e obrigatorio");
        this.previa = Objects.requireNonNull(previa, "A previa e obrigatoria");
        this.totalLinhas = totalLinhas;
        this.totalSolicitacoes = previa.solicitacoesPrevistas().size();
        this.criadoPor = Objects.requireNonNull(criadoPor, "O usuario criador e obrigatorio");
        this.criadoEm = Objects.requireNonNull(criadoEm, "O instante de criacao e obrigatorio");
        this.expiraEm = Objects.requireNonNull(expiraEm, "O instante de expiracao e obrigatorio");
        this.status = Status.VALIDADO;
        if (!expiraEm.isAfter(criadoEm)) {
            throw new IllegalArgumentException("A expiracao deve ser posterior a criacao");
        }
    }

    public boolean expiradaEm(OffsetDateTime instante) {
        return !expiraEm.isAfter(instante);
    }

    public void marcarExpirado() {
        status = Status.EXPIRADO;
    }

    public void registrarResultado(
            String chave,
            String tipo,
            PreviaImportacaoSolicitacaoTecnica.ResultadoConfirmacao resultado,
            UUID idUsuario,
            OffsetDateTime instante
    ) {
        previa = previa.comResultado(chave, tipo, resultado);
        confirmadoPor = Objects.requireNonNull(idUsuario, "O usuario confirmador e obrigatorio");
        confirmadoEm = Objects.requireNonNull(instante, "O instante de confirmacao e obrigatorio");
        boolean todosConfirmaveisConcluidos = previa.solicitacoesPrevistas().stream()
                .filter(PreviaImportacaoSolicitacaoTecnica.SolicitacaoPrevista::confirmavel)
                .allMatch(grupo -> grupo.subgrupos().stream()
                        .allMatch(subgrupo -> subgrupo.resultado() != null));
        status = todosConfirmaveisConcluidos ? Status.CONFIRMADO : Status.PARCIALMENTE_CONFIRMADO;
    }

    private static String exigirHash(String valor, String mensagem) {
        String texto = exigirTexto(valor, mensagem);
        if (!texto.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(mensagem);
        }
        return texto;
    }

    private static String exigirTexto(String valor, String mensagem) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensagem);
        }
        return valor.trim();
    }

    public enum Formato {
        CSV,
        XLSX
    }

    public enum Status {
        VALIDADO,
        PARCIALMENTE_CONFIRMADO,
        CONFIRMADO,
        EXPIRADO
    }
}
