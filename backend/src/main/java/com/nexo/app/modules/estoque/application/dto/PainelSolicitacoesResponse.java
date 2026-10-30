package com.nexo.app.modules.estoque.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Painel analítico de solicitações de material: uma linha por solicitação do recorte (mais o
 * backlog ainda aberto de antes do período), os materiais agregados e o resumo do período anterior.
 * Os indicadores, rankings e leituras gerenciais são calculados no cliente sobre essas linhas, para
 * que todo número do painel possa ser aberto até as solicitações que o compõem.
 */
@Schema(description = "Painel analítico de solicitações de material")
public record PainelSolicitacoesResponse(
    OffsetDateTime geradoEm,
    OffsetDateTime inicio,
    OffsetDateTime fim,
    @Schema(description = "Máximo de linhas devolvidas") int limite,
    @Schema(description = "Verdadeiro quando o recorte tinha mais solicitações que o limite") boolean truncado,
    List<Solicitacao> solicitacoes,
    List<Material> materiais,
    List<ResumoAnterior> periodoAnterior
) {

    @Schema(description = "Uma solicitação do recorte com seus marcos de tempo e quantidades")
    public record Solicitacao(
        UUID id,
        String contexto,
        String status,
        @Schema(description = "Sub-etapa operacional (mesma regra do Kanban)") String etapa,
        @Schema(description = "Macro-etapa operacional") String macroEtapa,
        @Schema(description = "221, 921, 222 ou 922 (somente técnico)") String tipoTecnico,
        String tipoObra,
        String ocorrencia,
        UUID idObra,
        String numeroObra,
        String statusSeparacao,
        OffsetDateTime dataSolicitacao,
        OffsetDateTime dataVerificacao,
        OffsetDateTime dataEntrega,
        LocalDate dataPrevista,
        OffsetDateTime separacaoIniciadaEm,
        OffsetDateTime separacaoConcluidaEm,
        OffsetDateTime encerradaEm,
        String estoqueOrigem,
        String estoqueDestino,
        UUID idAlmoxarifado,
        String almoxarifado,
        UUID idEquipe,
        String equipe,
        String baseOperacional,
        String processo,
        String motivoRejeicao,
        boolean possuiPendencia,
        String motivosPendencia,
        boolean foraPrazoSaque,
        boolean saqueForaEquipe,
        boolean temReserva,
        long itens,
        BigDecimal quantidadeSolicitada,
        BigDecimal quantidadeAtendida,
        @Schema(description = "Criada antes do período e ainda aberta (backlog)") boolean anteriorAoPeriodo
    ) {}

    @Schema(description = "Material agregado no período, separado por processo e sentido")
    public record Material(
        String contexto,
        @Schema(description = "SAIDA (comercial e adições técnicas) ou DEVOLUCAO") String sentido,
        String codigo,
        String descricao,
        String unidade,
        long solicitacoes,
        BigDecimal quantidadeSolicitada,
        BigDecimal quantidadeAtendida
    ) {}

    @Schema(description = "Resumo do período imediatamente anterior, de mesma duração")
    public record ResumoAnterior(
        String contexto,
        long total,
        long concluidas,
        long rejeitadas,
        Double cicloMedioHoras,
        Double aprovacaoMediaHoras
    ) {}
}
