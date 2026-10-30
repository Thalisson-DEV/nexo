package com.nexo.app.modules.financeiro.application.dto;

import com.nexo.app.modules.financeiro.domain.SituacaoDocumento;
import com.nexo.app.modules.financeiro.domain.TipoDocumentoFinanceiro;
import com.nexo.app.modules.financeiro.domain.TipoSolicitacao;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Um documento do repositório, pronto para a tela da contabilidade.
 *
 * <p>Toda referência vem com o texto legível ao lado ({@code fornecedorNome},
 * {@code baseNome}, ...) porque a tela lista dezenas de linhas de uma vez, e
 * resolver cadastro por linha no cliente significaria dezenas de requisições para
 * escrever nomes que o servidor já tinha em mãos.</p>
 */
@Schema(description = "Documento do repositório financeiro")
public record DocumentoFinanceiroResponse(

        UUID id,

        // ── Arquivo ─────────────────────────────────────────────────────────────
        String nomeArquivo,
        String contentType,
        Long tamanhoBytes,

        @Schema(description = "SHA-256 do conteúdo, usado para reconhecer o mesmo arquivo reenviado")
        String hashConteudo,

        @Schema(description = "Indica que existe outro documento ativo com conteúdo idêntico")
        boolean duplicado,

        // ── Classificação ───────────────────────────────────────────────────────
        TipoDocumentoFinanceiro tipo,
        String tipoRotulo,
        SituacaoDocumento situacao,
        String situacaoRotulo,
        String numeroDocumento,
        String chaveAcesso,

        @Schema(description = "Linha digitavel, codigo de barras ou PIX copia-e-cola")
        String codigoPagamento,

        boolean possuiCodigoPagamento,

        @Schema(description = "Nota ou boleto sem número informado — não serve como comprovação")
        boolean identificacaoPendente,

        // ── Emissor ─────────────────────────────────────────────────────────────
        UUID fornecedorId,

        @Schema(description = "Nome do fornecedor cadastrado ou, na falta dele, o emissor digitado")
        String emissorNome,

        // ── Datas e valor ───────────────────────────────────────────────────────
        LocalDate dataEmissao,
        LocalDate dataVencimento,

        @Schema(description = "Mês de competência contábil, sempre no dia 1", example = "2026-08-01")
        LocalDate competencia,

        @Schema(description = "Vencido e ainda em aberto na conferência")
        boolean vencido,

        @Schema(description = "Dias na situação atual, contados desde a última movimentação")
        long diasNaSituacao,

        BigDecimal valor,

        // ── Contexto ────────────────────────────────────────────────────────────
        UUID baseId,
        String baseNome,
        UUID processoId,
        String processoNome,
        UUID obraId,
        String obraNome,

        String descricao,
        String observacoes,

        // ── Origem ──────────────────────────────────────────────────────────────
        @Schema(description = "Tipo da solicitação de origem; nulo em documento avulso")
        TipoSolicitacao origemTipo,
        UUID origemSolicitacaoId,

        @Schema(description = "Número legível da solicitação de origem", example = "1042")
        Long origemNumero,

        // ── Trilha ──────────────────────────────────────────────────────────────
        UUID usuarioEnvioId,
        String usuarioEnvio,
        UUID usuarioConferenciaId,
        String usuarioConferencia,
        OffsetDateTime dataConferencia,
        String observacaoConferencia,
        String motivoPendencia,

        AcoesDocumentoResponse acoes,

        boolean ativo,
        OffsetDateTime dataCriacao,
        OffsetDateTime dataUltimaAlteracao,
        String usuarioUltimaAlteracao
) {}
