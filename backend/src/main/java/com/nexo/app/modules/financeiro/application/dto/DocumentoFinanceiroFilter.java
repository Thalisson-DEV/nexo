package com.nexo.app.modules.financeiro.application.dto;

import com.nexo.app.modules.financeiro.domain.SituacaoDocumento;
import com.nexo.app.modules.financeiro.domain.TipoDocumentoFinanceiro;
import com.nexo.app.modules.financeiro.domain.TipoSolicitacao;
import com.nexo.app.shared.application.SelecaoMultipla;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Filtros da busca no repositório documental.
 *
 * <p>É a tela de trabalho da contabilidade, e por isso o filtro é largo: procura-se
 * ora pelo número da nota, ora pelo fornecedor, ora por "tudo que vence esta
 * semana e ainda não foi conferido". Tipo, situação e fornecedor aceitam múltipla
 * seleção — repita o parâmetro ({@code ?tipo=NOTA_FISCAL&tipo=BOLETO}) ou separe
 * por vírgula. Valores da mesma dimensão combinam com OU; dimensões diferentes,
 * com E.</p>
 */
public record DocumentoFinanceiroFilter(

        @Schema(description = "Busca rápida por nome do arquivo, número do documento, emissor ou descrição")
        String buscaGlobal,

        List<TipoDocumentoFinanceiro> tipo,

        List<SituacaoDocumento> situacao,

        @Schema(description = "Fornecedor emissor, entre os cadastrados")
        List<UUID> fornecedorId,

        UUID baseId,

        @Schema(description = "Setor / centro de custo (processo de negócio)")
        UUID processoId,

        UUID obraId,

        @Schema(description = "Número exato do documento (nota, boleto, recibo)")
        String numeroDocumento,

        @Schema(description = "Chave de acesso da NF-e; aceita com ou sem formatação")
        String chaveAcesso,

        @Schema(description = "Restringe à origem: documentos de compras ou de pagamentos")
        TipoSolicitacao origemTipo,

        @Schema(description = "Documentos de uma solicitação específica")
        UUID origemSolicitacaoId,

        @Schema(description = "Número legível da solicitação de origem", example = "1042")
        Long origemNumero,

        @Schema(description = "Traz apenas documentos avulsos, sem solicitação de origem")
        Boolean apenasAvulsos,

        @Schema(description = "Competência contábil a partir de (qualquer dia do mês)")
        LocalDate competenciaInicio,

        @Schema(description = "Competência contábil até (qualquer dia do mês)")
        LocalDate competenciaFim,

        LocalDate emissaoInicio,

        LocalDate emissaoFim,

        LocalDate vencimentoInicio,

        LocalDate vencimentoFim,

        @Schema(description = "Traz apenas o que já venceu e continua em aberto na conferência")
        Boolean apenasVencidos,

        @Schema(description = "Traz apenas notas e boletos sem número informado")
        Boolean apenasSemIdentificacao,

        BigDecimal valorMinimo,

        BigDecimal valorMaximo,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime dataCriacaoInicio,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime dataCriacaoFim,

        @Schema(description = "Filtra por registro ativo (soft delete)")
        Boolean ativo
) {

    public DocumentoFinanceiroFilter {
        tipo = SelecaoMultipla.normalizar(tipo);
        situacao = SelecaoMultipla.normalizar(situacao);
        fornecedorId = SelecaoMultipla.normalizar(fornecedorId);
    }
}
