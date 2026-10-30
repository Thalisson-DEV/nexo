package com.nexo.app.modules.financeiro.application.dto;

import com.nexo.app.modules.financeiro.domain.DocumentoFinanceiro;
import com.nexo.app.modules.financeiro.domain.TipoDocumentoFinanceiro;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Metadados de um documento, informados no envio e em cada correção.
 *
 * <p>Quase tudo é opcional de propósito. O envio precisa ser barato: um boleto que
 * chega por e-mail entra no repositório com dois cliques e é classificado depois,
 * na conferência. Exigir número, competência e valor na porta de entrada faria a
 * pessoa guardar o arquivo em outro lugar — que é exatamente o problema que este
 * módulo existe para acabar.</p>
 */
@Schema(description = "Metadados de um documento financeiro")
public record DocumentoRequest(

        @Schema(description = "Natureza do documento. Omitido, assume OUTRO.", example = "NOTA_FISCAL")
        TipoDocumentoFinanceiro tipo,

        @Schema(description = "Número impresso no documento", example = "123456")
        @Size(max = 60, message = "O número do documento deve ter no máximo 60 caracteres.")
        String numeroDocumento,

        @Schema(description = "Chave de acesso da NF-e (44 dígitos); aceita com ou sem formatação")
        String chaveAcesso,

        @Schema(description = "Linha digitavel, codigo de barras ou PIX copia-e-cola", maxLength = 512)
        @Size(max = 512, message = "O codigo de pagamento deve ter no maximo 512 caracteres.")
        String codigoPagamento,

        @Schema(description = "Fornecedor emissor, quando cadastrado")
        UUID fornecedorId,

        @Schema(description = "Nome do emissor quando ele não está no cadastro")
        @Size(max = 200, message = "O nome do emissor deve ter no máximo 200 caracteres.")
        String emissorNome,

        LocalDate dataEmissao,

        LocalDate dataVencimento,

        @Schema(description = "Mês de competência contábil; qualquer dia do mês é aceito e "
                + "normalizado para o dia 1. Omitido, é deduzido do vencimento ou da emissão.")
        LocalDate competencia,

        @DecimalMin(value = "0.01", message = "O valor do documento deve ser maior que zero.")
        BigDecimal valor,

        UUID baseId,

        @Schema(description = "Setor / centro de custo (processo de negócio)")
        UUID processoId,

        UUID obraId,

        String descricao,

        String observacoes
) {

    /** Construtor de compatibilidade para integracoes que ainda nao enviam codigo pagavel. */
    public DocumentoRequest(TipoDocumentoFinanceiro tipo, String numeroDocumento, String chaveAcesso,
                            UUID fornecedorId, String emissorNome, LocalDate dataEmissao,
                            LocalDate dataVencimento, LocalDate competencia, BigDecimal valor,
                            UUID baseId, UUID processoId, UUID obraId, String descricao,
                            String observacoes) {
        this(tipo, numeroDocumento, chaveAcesso, null, fornecedorId, emissorNome,
                dataEmissao, dataVencimento, competencia, valor, baseId, processoId,
                obraId, descricao, observacoes);
    }

    /** Traduz o payload no formato que o domínio conhece. */
    public DocumentoFinanceiro.Metadados paraMetadados() {
        return new DocumentoFinanceiro.Metadados(
                tipo, numeroDocumento, chaveAcesso, codigoPagamento, fornecedorId, emissorNome,
                dataEmissao, dataVencimento, competencia, valor,
                baseId, processoId, obraId, descricao, observacoes);
    }

    /** Payload ausente é envio sem classificação — legítimo, e não erro. */
    public static DocumentoRequest vazio() {
        return deTipo(null);
    }

    /**
     * Envio rápido a partir da solicitação, em que a tela só pergunta a natureza.
     * O resto é herdado do formulário.
     */
    public static DocumentoRequest deTipo(TipoDocumentoFinanceiro tipo) {
        return new DocumentoRequest(tipo, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null);
    }
}
