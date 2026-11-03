package com.nexo.app.modules.obras.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nexo.app.modules.obras.domain.OperacaoItemKit;
import com.nexo.app.modules.obras.domain.StatusMedicao;
import com.nexo.app.modules.obras.domain.TipoMedicao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/** Leituras das medições, do pré-fechamento do levantamento e do fechamento de obra. */
public final class MedicaoResponses {

    private MedicaoResponses() {
    }

    /** Onde a obra está na carteira, com os nomes resolvidos, para filtrar e mostrar as listas no mesmo recorte. */
    public record ContextoObra(UUID idBaseOperacional, String baseOperacional, UUID idMunicipio, String municipio,
                               UUID idSupervisor, String supervisor, String statusObra, String prioridade) {
    }

    public record Opcao(UUID id, String nome) {
    }

    /** Municípios e supervisores das obras que têm medição, para os filtros da lista de medições. */
    public record OpcoesMedicoes(List<Opcao> municipios, List<Opcao> supervisores) {
    }

    /** Obra com serviço concluído ainda não medido. */
    public record ObraAMedir(UUID idObra, String numero, String titulo, ContextoObra contexto, long servicos, BigDecimal valor,
                             @JsonFormat(pattern = "dd/MM/yyyy") LocalDate ultimaExecucao) {
    }

    /** Serviço concluído pendente de medição. {@code valorTotal} nulo: o serviço não tinha valor ao ser programado. */
    public record ItemPendente(UUID idItem, String codigo, String descricao, String unidade, OperacaoItemKit operacao,
                               String codigoKit, String local, BigDecimal quantidade, BigDecimal valorTotal,
                               @JsonFormat(pattern = "dd/MM/yyyy") LocalDate concluidoEm) {
    }

    public record MedicaoResumo(UUID id, long numero, UUID idObra, String numeroObra, String tituloObra, TipoMedicao tipo,
                                @JsonFormat(pattern = "yyyy-MM") YearMonth competencia, StatusMedicao status,
                                BigDecimal valorTotal, int servicos, String observacao,
                                OffsetDateTime dataCriacao, String usuarioCriacao,
                                String justificativaEstorno, OffsetDateTime dataEstorno, String usuarioEstorno) {
    }

    public record ItemMedido(UUID id, UUID idItem, String codigo, String descricao, String unidade, OperacaoItemKit operacao,
                             String codigoKit, String local, BigDecimal quantidade, BigDecimal valorUnitario,
                             BigDecimal valorTotal, @JsonFormat(pattern = "dd/MM/yyyy") LocalDate concluidoEm) {
    }

    public record MedicaoDetalhe(MedicaoResumo medicao, List<ItemMedido> itens) {
    }

    /**
     * Tudo o que a tela de medição da obra precisa: o que está pendente, o que já foi medido e o saldo sobre o valor
     * de projeto. {@code servicosSemValor} conta os pendentes sem valor congelado, que entram com valor zero.
     */
    public record MedicaoDaObra(UUID idObra, String numero, String titulo, String municipio, BigDecimal valorProjeto,
                                List<ItemPendente> pendentes, BigDecimal valorPendente, int servicosSemValor,
                                BigDecimal valorMedido, BigDecimal saldo, List<MedicaoResumo> medicoes) {
    }

    /**
     * Medição por ciclo: valor de projeto, medido nas competências anteriores, medido nesta e saldos. Saldos ficam
     * nulos quando a obra não tem valor de projeto.
     */
    public record Ciclo(UUID idObra, String numero, String titulo,
                        @JsonFormat(pattern = "yyyy-MM") YearMonth competencia,
                        @JsonFormat(pattern = "dd/MM/yyyy") LocalDate inicio,
                        @JsonFormat(pattern = "dd/MM/yyyy") LocalDate fim,
                        BigDecimal valorProjeto, BigDecimal medidoAnterior, BigDecimal saldoAnterior,
                        BigDecimal medidoNoCiclo, BigDecimal saldoAposCiclo,
                        List<ItemPendente> aMedirNoCiclo, BigDecimal valorAMedir, List<MedicaoResumo> medicoesDoCiclo) {
    }

    /** Situação do levantamento de uma obra. {@code prontoParaConcluir}: pendente, com pontos e sem nada sem kit. */
    public record LinhaPreFechamento(UUID idObra, String numero, String titulo, ContextoObra contexto, String tipoObra,
                                     int pontos, int vaos, int pontosSemVao, int pontosSemKit, int vaosSemKit,
                                     int semDesligamento, int comDesligamento, int naoDefinido,
                                     List<String> levantadores, boolean concluido, OffsetDateTime concluidoEm,
                                     String concluidoPor, boolean prontoParaConcluir) {
    }

    /** Linhas e indicadores do recorte (antes do filtro "apenas pendentes"). */
    public record PreFechamento(List<LinhaPreFechamento> linhas, int obras, int pendentes, int prontas, int concluidas,
                                int pontosSemKit, int vaosSemKit) {
    }

    public record LinhaServico(String codigo, String descricao, OperacaoItemKit operacao, String unidade,
                               BigDecimal lancado, BigDecimal executado, BigDecimal valorExecutado) {
    }

    public record LinhaMaterial(String codigo, String descricao, OperacaoItemKit operacao, String unidade,
                                BigDecimal lancado, BigDecimal aplicado) {
    }

    public record LinhaKit(String codigoKit, String descricao, int lancados, int concluidos, List<String> locais) {
    }

    /** Situação da execução: NAO_INICIADA, EM_ANDAMENTO ou CONCLUIDA (todos os kits concluídos). */
    public record FechamentoObra(UUID idObra, String numero, String titulo, String municipio, String situacao,
                                 int kitsLancados, int kitsConcluidos, BigDecimal valorPlanejado,
                                 BigDecimal valorExecutado, BigDecimal valorMedido, int medicoesAtivas,
                                 List<LinhaServico> servicos, List<LinhaMaterial> materiais, List<LinhaKit> kits,
                                 List<MedicaoResumo> medicoes) {
    }

    public record Fechamento(List<FechamentoObra> obras, List<String> naoEncontrados) {
    }

    /**
     * Obra reconhecida na planilha orçado × faturado. {@code faturado} soma os lançamentos aprovados (crédito subtrai);
     * {@code paraConferir} conta os não aprovados e os créditos. Com {@code erros}, o relatório da obra não é gerado.
     */
    public record ObraNaPlanilha(UUID idObra, String numero, String titulo, String municipio,
                                 int itensMaoDeObra, int itensMateriais, int lancamentosFaturados,
                                 BigDecimal orcadoMaoDeObra, BigDecimal orcadoMateriais, BigDecimal faturado,
                                 LocalDate ultimoFaturamento, int paraConferir, List<String> erros) {
    }

    /** Leitura da planilha: obras reconhecidas, números sem obra cadastrada e erros de linhas sem obra. */
    public record AnaliseOrcadoFaturado(List<ObraNaPlanilha> obras, List<String> naoIdentificadas, List<String> errosSemObra,
                                        int linhasMaoDeObra, int linhasMateriais, int linhasFaturado) {
    }
}
