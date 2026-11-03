package com.nexo.app.modules.obras.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nexo.app.modules.obras.domain.PrioridadeObra;
import com.nexo.app.modules.obras.domain.SituacaoIntervencao;
import com.nexo.app.modules.obras.domain.SituacaoProgramacao;
import com.nexo.app.modules.obras.domain.StatusItemPlanejado;
import com.nexo.app.modules.obras.domain.TipoEventoProgramacao;
import com.nexo.app.modules.obras.domain.TipoItemKit;
import com.nexo.app.modules.obras.domain.TurnoProgramacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Leituras da programação: prévia do diálogo, detalhe, Gantt e histórico. */
public final class ProgramacaoResponses {

    private ProgramacaoResponses() {
    }

    /**
     * O que será programado e as equipes possíveis. {@code ignoradosProgramados} e {@code ignoradosConcluidos}
     * contam o que veio na seleção e fica de fora; {@code materiaisIncluidos}, o material que acompanha o serviço.
     */
    public record Previa(int itens, int servicos, int materiais, int materiaisIncluidos,
                         int ignoradosProgramados, int ignoradosConcluidos, int servicosSemValor,
                         List<String> nomesServicosSemValor, BigDecimal valor,
                         @JsonFormat(pattern = "dd/MM/yyyy") LocalDate prazoPrevisto,
                         @JsonFormat(pattern = "dd/MM/yyyy") LocalDate prazoRegulado,
                         List<EquipeProjetada> equipes) {
    }

    /**
     * Projeção da equipe no período. {@code capacidadeMinutos} é jornada − plataforma − deslocamento ida e volta
     * e pode ser negativa, o que alerta sem impedir. Sem coordenadas, o deslocamento fica nulo e não é descontado.
     * Sem meta vigente, os campos de meta e os percentuais ficam nulos. {@code conflitos} são as outras programações
     * da equipe nos mesmos dias: a tela confere o turno e avisa, sem impedir.
     */
    public record EquipeProjetada(UUID id, String codigo, UUID idTipoEquipe, String tipoEquipe, UUID idBase, String base,
                                  BigDecimal distanciaKm, Integer tempoDeslocamentoMin, Boolean deslocamentoEstimado,
                                  String semDeslocamentoPorque,
                                  Integer jornadaMinutos, Integer plataformaMinutos, Integer capacidadeMinutos,
                                  int diasUteis, BigDecimal metaDia, BigDecimal metaPeriodo,
                                  BigDecimal valorProgramadoPeriodo, BigDecimal valorSelecao,
                                  BigDecimal percentualOcupacao, BigDecimal percentualMeta, List<Conflito> conflitos) {
    }

    public record Conflito(long numero, String numeroObra,
                           @JsonFormat(pattern = "dd/MM/yyyy") LocalDate inicio,
                           @JsonFormat(pattern = "dd/MM/yyyy") LocalDate fim,
                           TurnoProgramacao turno) {
    }

    /** Situação do planejamento de uma obra em valor de serviço, para listas. */
    public record ResumoPlanejamento(UUID idObra, boolean temKits, BigDecimal valorTotal, BigDecimal valorProgramado,
                                     BigDecimal valorConcluido, BigDecimal valorPendente, BigDecimal percentualProgramado,
                                     BigDecimal percentualConcluido, int servicosPendentes, int pontosSemKit) {
    }

    /** Obra que ainda tem serviço por programar, com o prazo que manda na prioridade. */
    public record ObraAProgramar(UUID idObra, String numero, String titulo, String prioridade,
                                 @JsonFormat(pattern = "dd/MM/yyyy") LocalDate prazoPrevisto,
                                 @JsonFormat(pattern = "dd/MM/yyyy") LocalDate prazoRegulado,
                                 BigDecimal valorPendente, int servicosPendentes, int pontosSemKit, BigDecimal percentualProgramado) {
    }

    public record Resultado(ProgramacaoDetalhe programacao, int ignoradosProgramados, int ignoradosConcluidos,
                            int materiaisIncluidos, int servicosSemValor) {
    }

    public record ProgramacaoDetalhe(UUID id, long numero, UUID idObra, String numeroObra, String tituloObra,
                                     UUID idEquipe, String codigoEquipe,
                                     @JsonFormat(pattern = "dd/MM/yyyy") LocalDate inicio,
                                     @JsonFormat(pattern = "dd/MM/yyyy") LocalDate fim,
                                     String descricao, boolean linhaViva, boolean desligamento, boolean finalizaObra,
                                     TurnoProgramacao turno, SituacaoIntervencao situacaoIntervencao, String observacao,
                                     SituacaoProgramacao situacao, BigDecimal valor, String criadoPor,
                                     OffsetDateTime criadoEm, Long versao, List<ItemProgramado> itens) {
    }

    public record ItemProgramado(UUID idItem, String local, TipoItemKit tipo, String codigo, String descricao,
                                 String unidade, BigDecimal quantidade, BigDecimal quantidadeExecutada,
                                 BigDecimal valorUnitario, BigDecimal valorTotal, StatusItemPlanejado status) {
    }

    public record Gantt(@JsonFormat(pattern = "dd/MM/yyyy") LocalDate inicio,
                        @JsonFormat(pattern = "dd/MM/yyyy") LocalDate fim,
                        List<LinhaGantt> equipes,
                        List<BarraGantt> atrasadasAntes) {
    }

    public record LinhaGantt(UUID idEquipe, String codigo, String tipoEquipe, String base, BigDecimal valorProgramado,
                             BigDecimal percentualMeta, String supervisor, String coordenador,
                             List<BarraGantt> programacoes) {
    }

    public record BarraGantt(UUID id, long numero, UUID idObra, String numeroObra, String tituloObra,
                             @JsonFormat(pattern = "dd/MM/yyyy") LocalDate inicio,
                             @JsonFormat(pattern = "dd/MM/yyyy") LocalDate fim,
                             TurnoProgramacao turno, SituacaoProgramacao situacao, BigDecimal valor, int itens, Long versao,
                             UUID idEquipe, String codigoEquipe, boolean linhaViva, boolean desligamento, boolean finalizaObra,
                             SituacaoIntervencao situacaoIntervencao, String municipio, PrioridadeObra prioridade) {
    }

    public record Evento(UUID id, OffsetDateTime data, TipoEventoProgramacao tipo, UUID idProgramacao, long numeroProgramacao,
                         UUID idObra, String numeroObra, String equipeOrigem, String equipeDestino,
                         @JsonFormat(pattern = "dd/MM/yyyy") LocalDate inicioOriginal,
                         @JsonFormat(pattern = "dd/MM/yyyy") LocalDate fimOriginal,
                         @JsonFormat(pattern = "dd/MM/yyyy") LocalDate inicioNovo,
                         @JsonFormat(pattern = "dd/MM/yyyy") LocalDate fimNovo,
                         String usuario, String observacao) {
    }
}
