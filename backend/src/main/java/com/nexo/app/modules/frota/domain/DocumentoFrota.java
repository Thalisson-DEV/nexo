package com.nexo.app.modules.frota.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Documento de um veículo ou de um implemento, com vencimento e arquivo opcional.
 *
 * <p>Pertence a exatamente um titular. O arquivo mora no armazenamento de objetos;
 * aqui ficam só a chave e os metadados para download e conferência de integridade.</p>
 */
@Entity
@Table(name = "frota_documentos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentoFrota extends BaseEntity {

    @Column(name = "id_veiculo", updatable = false)
    private UUID idVeiculo;

    @Column(name = "id_implemento", updatable = false)
    private UUID idImplemento;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private TipoDocumentoFrota tipo;

    @Column(name = "descricao", length = 150)
    private String descricao;

    @Column(name = "numero", length = 80)
    private String numero;

    @Column(name = "data_emissao")
    private LocalDate dataEmissao;

    @Column(name = "data_vencimento")
    private LocalDate dataVencimento;

    @Column(name = "observacao", length = 1000)
    private String observacao;

    @Column(name = "arquivo_chave", length = 400)
    private String arquivoChave;

    @Column(name = "arquivo_nome", length = 255)
    private String arquivoNome;

    @Column(name = "arquivo_content_type", length = 150)
    private String arquivoContentType;

    @Column(name = "arquivo_tamanho_bytes")
    private Long arquivoTamanhoBytes;

    @Column(name = "arquivo_sha256", length = 64)
    private String arquivoSha256;

    public static DocumentoFrota doVeiculo(UUID idVeiculo, DadosDocumento dados) {
        if (idVeiculo == null) throw new InvarianteVioladaException("Informe o veículo do documento.");
        var documento = new DocumentoFrota();
        documento.idVeiculo = idVeiculo;
        documento.preencher(dados);
        return documento;
    }

    public static DocumentoFrota doImplemento(UUID idImplemento, DadosDocumento dados) {
        if (idImplemento == null) throw new InvarianteVioladaException("Informe o implemento do documento.");
        var documento = new DocumentoFrota();
        documento.idImplemento = idImplemento;
        documento.preencher(dados);
        return documento;
    }

    public void atualizar(DadosDocumento dados) {
        if (!isAtivo()) {
            throw new InvarianteVioladaException("Não é possível alterar um documento excluído.");
        }
        preencher(dados);
    }

    /** Liga o arquivo enviado ao documento, substituindo o anterior. Devolve a chave antiga. */
    public String anexarArquivo(String chave, String nome, String contentType, long tamanhoBytes, String sha256) {
        if (chave == null || chave.isBlank()) throw new InvarianteVioladaException("Arquivo inválido.");
        if (tamanhoBytes <= 0) throw new InvarianteVioladaException("O arquivo está vazio.");
        String anterior = this.arquivoChave;
        this.arquivoChave = chave;
        this.arquivoNome = Textos.obrigatorio(nome, "nome do arquivo", 255);
        this.arquivoContentType = contentType;
        this.arquivoTamanhoBytes = tamanhoBytes;
        this.arquivoSha256 = sha256 == null ? null : sha256.toLowerCase(Locale.ROOT);
        return anterior;
    }

    public boolean possuiArquivo() {
        return arquivoChave != null;
    }

    /** Dias até o vencimento (negativo se já venceu); nulo quando o documento não vence. */
    public Long diasParaVencer(LocalDate hoje) {
        return dataVencimento == null ? null : ChronoUnit.DAYS.between(hoje, dataVencimento);
    }

    public SituacaoVencimento situacaoEm(LocalDate hoje, int diasAviso) {
        return SituacaoVencimento.calcular(dataVencimento, hoje, diasAviso);
    }

    private void preencher(DadosDocumento dados) {
        Objects.requireNonNull(dados, "Os dados do documento são obrigatórios");
        if (dados.tipo() == null) throw new InvarianteVioladaException("Informe o tipo do documento.");
        if (dados.tipo() == TipoDocumentoFrota.OUTRO && (dados.descricao() == null || dados.descricao().isBlank())) {
            throw new InvarianteVioladaException("Descreva o documento quando o tipo for \"Outro\".");
        }
        if (dados.dataEmissao() != null && dados.dataVencimento() != null
                && dados.dataVencimento().isBefore(dados.dataEmissao())) {
            throw new InvarianteVioladaException("O vencimento não pode ser anterior à emissão.");
        }
        this.tipo = dados.tipo();
        this.descricao = Textos.opcional(dados.descricao(), 150);
        this.numero = Textos.opcional(dados.numero(), 80);
        this.dataEmissao = dados.dataEmissao();
        this.dataVencimento = dados.dataVencimento();
        this.observacao = Textos.opcional(dados.observacao(), 1000);
    }

    /** Valores editáveis do documento. */
    public record DadosDocumento(TipoDocumentoFrota tipo, String descricao, String numero,
                                 LocalDate dataEmissao, LocalDate dataVencimento, String observacao) {
    }
}
