package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes de invariantes e do cálculo dinâmico das janelas de apuração da
 * {@link MetaInspecao}.
 */
class MetaInspecaoTest {

    private static final UUID TIPO_INSPECAO = UUID.randomUUID();
    private static final UUID FUNCAO = UUID.randomUUID();
    private static final UUID COLABORADOR = UUID.randomUUID();
    private static final LocalDate INICIO = LocalDate.of(2026, 3, 1);

    private MetaInspecao metaMensalDeFuncao() {
        return new MetaInspecao(TIPO_INSPECAO, FUNCAO, null, 4, PeriodicidadeMeta.MENSAL, INICIO, null, "Meta mensal");
    }

    @Test
    @DisplayName("Deve derivar o escopo a partir do alvo informado")
    void deveDerivarEscopo() {
        MetaInspecao daFuncao = metaMensalDeFuncao();
        MetaInspecao doColaborador = new MetaInspecao(
                TIPO_INSPECAO, null, COLABORADOR, 2, PeriodicidadeMeta.MENSAL, INICIO, null, null);

        assertEquals(EscopoMeta.FUNCAO, daFuncao.getEscopo());
        assertEquals(FUNCAO, daFuncao.getIdFuncao());
        assertNull(daFuncao.getIdColaborador());

        assertEquals(EscopoMeta.COLABORADOR, doColaborador.getEscopo());
        assertEquals(COLABORADOR, doColaborador.getIdColaborador());
        assertNull(doColaborador.getIdFuncao());
    }

    @Test
    @DisplayName("Não deve aceitar meta sem alvo ou com os dois alvos")
    void deveExigirExatamenteUmAlvo() {
        assertThrows(InvarianteVioladaException.class, () ->
                new MetaInspecao(TIPO_INSPECAO, null, null, 4, PeriodicidadeMeta.MENSAL, INICIO, null, null));

        assertThrows(InvarianteVioladaException.class, () ->
                new MetaInspecao(TIPO_INSPECAO, FUNCAO, COLABORADOR, 4, PeriodicidadeMeta.MENSAL, INICIO, null, null));
    }

    @Test
    @DisplayName("Não deve aceitar quantidade menor ou igual a zero")
    void deveExigirQuantidadePositiva() {
        assertThrows(InvarianteVioladaException.class, () ->
                new MetaInspecao(TIPO_INSPECAO, FUNCAO, null, 0, PeriodicidadeMeta.MENSAL, INICIO, null, null));
    }

    @Test
    @DisplayName("Não deve aceitar vigência com fim anterior ao início")
    void deveExigirVigenciaCoerente() {
        assertThrows(InvarianteVioladaException.class, () ->
                new MetaInspecao(TIPO_INSPECAO, FUNCAO, null, 4, PeriodicidadeMeta.MENSAL, INICIO, INICIO.minusDays(1), null));
    }

    @Test
    @DisplayName("PERSONALIZADO exige data de fim, pois a janela é a própria vigência")
    void personalizadoDeveExigirFim() {
        assertThrows(InvarianteVioladaException.class, () ->
                new MetaInspecao(TIPO_INSPECAO, FUNCAO, null, 10, PeriodicidadeMeta.PERSONALIZADO, INICIO, null, null));

        MetaInspecao campanha = new MetaInspecao(
                TIPO_INSPECAO, FUNCAO, null, 10, PeriodicidadeMeta.PERSONALIZADO, INICIO, INICIO.plusDays(44), "Campanha");

        JanelaMeta janela = campanha.janelaEm(INICIO.plusDays(10)).orElseThrow();
        assertEquals(INICIO, janela.inicio());
        assertEquals(INICIO.plusDays(44), janela.fim());
        assertEquals(1, janela.ciclo());
    }

    @Test
    @DisplayName("Meta mensal deve gerar a janela do mês corrente a partir da âncora")
    void deveCalcularJanelaMensal() {
        MetaInspecao meta = metaMensalDeFuncao();

        JanelaMeta primeira = meta.janelaEm(LocalDate.of(2026, 3, 15)).orElseThrow();
        assertEquals(LocalDate.of(2026, 3, 1), primeira.inicio());
        assertEquals(LocalDate.of(2026, 3, 31), primeira.fim());
        assertEquals(1, primeira.ciclo());

        JanelaMeta terceira = meta.janelaEm(LocalDate.of(2026, 5, 20)).orElseThrow();
        assertEquals(LocalDate.of(2026, 5, 1), terceira.inicio());
        assertEquals(LocalDate.of(2026, 5, 31), terceira.fim());
        assertEquals(3, terceira.ciclo());
    }

    @Test
    @DisplayName("Meta trimestral e semanal devem gerar janelas contíguas a partir da âncora")
    void deveCalcularJanelasDeOutrasPeriodicidades() {
        MetaInspecao trimestral = new MetaInspecao(
                TIPO_INSPECAO, FUNCAO, null, 12, PeriodicidadeMeta.TRIMESTRAL, INICIO, null, null);
        JanelaMeta trimestre = trimestral.janelaEm(LocalDate.of(2026, 7, 10)).orElseThrow();
        assertEquals(LocalDate.of(2026, 6, 1), trimestre.inicio());
        assertEquals(LocalDate.of(2026, 8, 31), trimestre.fim());
        assertEquals(2, trimestre.ciclo());

        MetaInspecao semanal = new MetaInspecao(
                TIPO_INSPECAO, FUNCAO, null, 1, PeriodicidadeMeta.SEMANAL, INICIO, null, null);
        JanelaMeta semana = semanal.janelaEm(LocalDate.of(2026, 3, 10)).orElseThrow();
        assertEquals(LocalDate.of(2026, 3, 8), semana.inicio());
        assertEquals(LocalDate.of(2026, 3, 14), semana.fim());
    }

    @Test
    @DisplayName("A janela deve ser truncada no fim da vigência")
    void deveTruncarJanelaNoFimDaVigencia() {
        MetaInspecao meta = new MetaInspecao(
                TIPO_INSPECAO, FUNCAO, null, 4, PeriodicidadeMeta.MENSAL, INICIO, LocalDate.of(2026, 4, 15), null);

        JanelaMeta janela = meta.janelaEm(LocalDate.of(2026, 4, 10)).orElseThrow();
        assertEquals(LocalDate.of(2026, 4, 1), janela.inicio());
        assertEquals(LocalDate.of(2026, 4, 15), janela.fim());
    }

    @Test
    @DisplayName("Não deve haver janela fora da vigência ou com a meta desativada")
    void naoDeveHaverJanelaForaDaVigencia() {
        MetaInspecao meta = new MetaInspecao(
                TIPO_INSPECAO, FUNCAO, null, 4, PeriodicidadeMeta.MENSAL, INICIO, LocalDate.of(2026, 4, 30), null);

        assertTrue(meta.janelaEm(INICIO.minusDays(1)).isEmpty(), "antes do início da vigência");
        assertTrue(meta.janelaEm(LocalDate.of(2026, 5, 1)).isEmpty(), "depois do fim da vigência");

        assertTrue(meta.janelaEm(LocalDate.of(2026, 4, 10)).isPresent());
        meta.desativar();
        assertTrue(meta.janelaEm(LocalDate.of(2026, 4, 10)).isEmpty(), "meta desativada não gera janela");
    }

    @Test
    @DisplayName("A meta diária deve diluir a quantidade pelos dias da janela")
    void deveRatearMetaPorDia() {
        MetaInspecao mensal = new MetaInspecao(
                TIPO_INSPECAO, FUNCAO, null, 4, PeriodicidadeMeta.MENSAL, LocalDate.of(2026, 8, 1), null, null);

        // Agosto tem 31 dias: 4/31 = 0,129032
        assertEquals(new BigDecimal("0.129032"),
                mensal.metaDiariaEm(LocalDate.of(2026, 8, 15)).orElseThrow());

        // A soma dos dias da janela reconstrói a meta cheia
        BigDecimal soma = LocalDate.of(2026, 8, 1).datesUntil(LocalDate.of(2026, 9, 1))
                .map(dia -> mensal.metaDiariaEm(dia).orElseThrow())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("4.00"), soma.setScale(2, RoundingMode.HALF_UP));

        assertTrue(mensal.metaDiariaEm(LocalDate.of(2026, 7, 31)).isEmpty(), "fora da vigência");
    }

    @Test
    @DisplayName("Deve acusar conflito entre metas do mesmo alvo com vigências sobrepostas")
    void deveDetectarConflito() {
        MetaInspecao indeterminada = metaMensalDeFuncao();
        MetaInspecao sobreposta = new MetaInspecao(
                TIPO_INSPECAO, FUNCAO, null, 6, PeriodicidadeMeta.MENSAL, LocalDate.of(2026, 6, 1), null, null);
        MetaInspecao anteriorEncerrada = new MetaInspecao(
                TIPO_INSPECAO, FUNCAO, null, 6, PeriodicidadeMeta.MENSAL,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28), null);
        MetaInspecao deOutraFuncao = new MetaInspecao(
                TIPO_INSPECAO, UUID.randomUUID(), null, 6, PeriodicidadeMeta.MENSAL, INICIO, null, null);
        MetaInspecao deColaborador = new MetaInspecao(
                TIPO_INSPECAO, null, COLABORADOR, 6, PeriodicidadeMeta.MENSAL, INICIO, null, null);
        MetaInspecao outroTipo = new MetaInspecao(
                UUID.randomUUID(), FUNCAO, null, 6, PeriodicidadeMeta.MENSAL, LocalDate.of(2026, 6, 1), null, null);

        assertTrue(indeterminada.conflitaCom(sobreposta));
        assertFalse(indeterminada.conflitaCom(anteriorEncerrada));
        assertFalse(indeterminada.conflitaCom(deOutraFuncao));
        assertFalse(indeterminada.conflitaCom(deColaborador));
        assertFalse(indeterminada.conflitaCom(outroTipo));
    }

    @Test
    @DisplayName("atualizar deve revalidar os dados e preservar o alvo")
    void atualizarDeveRevalidar() {
        MetaInspecao meta = metaMensalDeFuncao();

        meta.atualizar(8, PeriodicidadeMeta.BIMESTRAL, INICIO, LocalDate.of(2026, 12, 31), "Nova meta");

        assertEquals(8, meta.getQuantidade());
        assertEquals(PeriodicidadeMeta.BIMESTRAL, meta.getPeriodicidade());
        assertEquals(LocalDate.of(2026, 12, 31), meta.getVigenciaFim());
        assertEquals("Nova meta", meta.getDescricao());
        assertEquals(FUNCAO, meta.getIdFuncao());

        assertThrows(InvarianteVioladaException.class, () ->
                meta.atualizar(0, PeriodicidadeMeta.MENSAL, INICIO, null, null));
    }
}
