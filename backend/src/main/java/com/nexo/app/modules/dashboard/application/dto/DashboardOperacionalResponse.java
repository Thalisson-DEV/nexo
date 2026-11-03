package com.nexo.app.modules.dashboard.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Produção das equipes de obras num período: quanto foi executado nas obras (apontamentos), como isso se
 * compara à meta de cada equipe e se o ritmo atual fecha a meta.
 *
 * <p>O "ritmo" compara a produção com a meta esperada até o último dia útil já
 * encerrado (o dia corrente ainda está em andamento e não entra na conta). A
 * projeção soma à produção atual o ritmo médio desses dias úteis aplicado aos dias
 * úteis que ainda faltam no período.</p>
 */
public record DashboardOperacionalResponse(
        LocalDate inicio,
        LocalDate fim,
        OffsetDateTime geradoEm,
        int diasUteisPeriodo,
        int diasUteisEncerrados,
        Resumo resumo,
        List<ProducaoDia> producaoDiaria,
        /* Obras com execução no período, da maior produção para a menor. */
        List<ProducaoPorObra> obras,
        List<Equipe> equipes
) {

    public record Resumo(
            BigDecimal producao,
            BigDecimal producaoPeriodoAnterior,
            /* Variação percentual contra o período anterior de mesmo tamanho; nula sem base. */
            BigDecimal variacaoPercentual,
            /* Obras distintas com execução no período. */
            long obrasAtendidas,
            BigDecimal valorMedioObra,
            /* Serviços apontados (itens de serviço; materiais não contam). */
            long servicos,
            int equipesAtivas,
            int equipesComProducao,
            int equipesComMeta,
            /* Soma das metas proporcionais das equipes com meta cadastrada. */
            BigDecimal metaPeriodo,
            /* Produção apenas das equipes com meta — o numerador comparável a metaPeriodo. */
            BigDecimal producaoEquipesComMeta,
            BigDecimal percentualMeta,
            BigDecimal projecaoEquipesComMeta,
            int equipesAbaixoDoRitmo
    ) {
    }

    public record Equipe(
            UUID idEquipe,
            String codigo,
            String baseOperacional,
            String processo,
            String tipoEquipe,
            String placa,
            BigDecimal producao,
            long obrasAtendidas,
            BigDecimal valorMedioObra,
            long servicos,
            long diasComTurno,
            long diasComProducao,
            BigDecimal producaoMediaPorDia,
            BigDecimal metaMensal,
            BigDecimal metaPeriodo,
            BigDecimal metaEsperadaAteAgora,
            BigDecimal percentualMeta,
            BigDecimal projecao,
            SituacaoMeta situacao,
            /* Dia do último apontamento da equipe no período. */
            LocalDate ultimoApontamento,
            List<ProducaoDia> producaoDiaria,
            /* Obras em que a equipe executou no período, da maior produção para a menor. */
            List<ProducaoPorObra> obras
    ) {
    }

    /**
     * Produção de um dia; o período inteiro vem preenchido, inclusive dias sem execução.
     * {@code meta} é a parte da meta mensal que cabe ao dia (zero em dia não útil) e fica
     * nula sem meta cadastrada; na série geral é a soma das equipes com meta.
     */
    public record ProducaoDia(LocalDate data, long obras, long servicos, BigDecimal valor, BigDecimal meta) {
    }

    /** Execução de uma obra no recorte. */
    public record ProducaoPorObra(
            UUID idObra,
            String numero,
            String titulo,
            /* Equipes que executaram na obra no recorte. */
            int equipes,
            long servicos,
            long diasComExecucao,
            BigDecimal producao,
            /* Fatia da produção do recorte, em percentual com uma casa. */
            BigDecimal percentualProducao,
            LocalDate ultimoApontamento
    ) {
    }

    public enum SituacaoMeta {
        META_ATINGIDA,
        NO_RITMO,
        ABAIXO_DO_RITMO,
        SEM_META
    }
}
