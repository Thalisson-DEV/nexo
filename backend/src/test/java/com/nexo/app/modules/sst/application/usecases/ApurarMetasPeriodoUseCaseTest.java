package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.rh.BaseOperacionalResponse;
import com.nexo.app.modules.rh.CentroFaturamentoResponse;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ColaboradorResponse;
import com.nexo.app.modules.rh.FuncaoResponse;
import com.nexo.app.modules.sst.application.dto.ApuracaoMetaPeriodo;
import com.nexo.app.modules.sst.domain.MetaInspecao;
import com.nexo.app.modules.sst.domain.PeriodicidadeMeta;
import com.nexo.app.modules.sst.infrastructure.persistence.MetaInspecaoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApurarMetasPeriodoUseCaseTest {

    private static final UUID TIPO_CAMPO = UUID.randomUUID();
    private static final UUID TIPO_ALOJAMENTO = UUID.randomUUID();
    private static final UUID FUNCAO = UUID.randomUUID();
    private static final UUID BASE = UUID.randomUUID();
    private static final UUID OUTRA_BASE = UUID.randomUUID();
    private static final UUID CENTRO_NORTE = UUID.randomUUID();
    private static final UUID CENTRO_SUL = UUID.randomUUID();

    private static final UUID ANA = UUID.randomUUID();
    private static final UUID ANA_USUARIO = UUID.randomUUID();
    private static final UUID BRUNO = UUID.randomUUID();
    private static final UUID BRUNO_USUARIO = UUID.randomUUID();

    private static final LocalDate INICIO_AGOSTO = LocalDate.of(2026, 8, 1);
    private static final LocalDate FIM_AGOSTO = LocalDate.of(2026, 8, 31);

    @Mock
    private MetaInspecaoRepository metaRepository;
    @Mock
    private ColaboradorApi colaboradorApi;

    @InjectMocks
    private ApurarMetasPeriodoUseCase useCase;

    private MetaInspecao metaMensalDaFuncao(int quantidade) {
        return new MetaInspecao(TIPO_CAMPO, FUNCAO, null, quantidade, PeriodicidadeMeta.MENSAL,
                LocalDate.of(2026, 1, 1), null, "Meta mensal");
    }

    private ColaboradorResponse colaborador(UUID id, UUID idUsuario, String nome, UUID idBase) {
        return colaborador(id, idUsuario, nome, idBase, null);
    }

    private ColaboradorResponse colaborador(UUID id, UUID idUsuario, String nome, UUID idBase, UUID idCentroCusto) {
        CentroFaturamentoResponse centro = idCentroCusto == null ? null
                : new CentroFaturamentoResponse(idCentroCusto, "CENTRO", true);
        return new ColaboradorResponse(id, idUsuario, nome, "SILVA", nome + " SILVA", "00000000000",
                null, new BaseOperacionalResponse(
                        idBase, "BASE", null, null, centro, null, null, null, true),
                new FuncaoResponse(FUNCAO, "ENCARREGADO", null, true), LocalDate.of(2020, 1, 1), true);
    }

    private BigDecimal duasCasas(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    @Test
    @DisplayName("Mês inteiro deve reproduzir a meta cheia de cada colaborador da função")
    void mesInteiroReproduzMetaCheia() {
        when(metaRepository.findByAtivoTrue()).thenReturn(List.of(metaMensalDaFuncao(4)));
        when(colaboradorApi.listarAtivosPorFuncao(FUNCAO)).thenReturn(List.of(
                colaborador(ANA, ANA_USUARIO, "ANA", BASE),
                colaborador(BRUNO, BRUNO_USUARIO, "BRUNO", BASE)));

        ApuracaoMetaPeriodo apuracao = useCase.apurar(INICIO_AGOSTO, FIM_AGOSTO, null, null, null);

        assertEquals(new BigDecimal("8.00"), duasCasas(apuracao.metaTotal()));
        assertEquals(new BigDecimal("4.00"), duasCasas(apuracao.metaPorInspetor().get(ANA_USUARIO)));
        assertEquals(new BigDecimal("4.00"), duasCasas(apuracao.metaPorInspetor().get(BRUNO_USUARIO)));
        assertEquals(2, apuracao.colaboradoresComMeta());
        assertEquals(31, apuracao.metaPorDia().size());
        assertEquals(new BigDecimal("8.00"), duasCasas(apuracao.metaPorDia().values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)));
    }

    @Test
    @DisplayName("Período parcial deve receber apenas a fração proporcional da meta")
    void periodoParcialRecebeFracao() {
        when(metaRepository.findByAtivoTrue()).thenReturn(List.of(metaMensalDaFuncao(4)));
        when(colaboradorApi.listarAtivosPorFuncao(FUNCAO)).thenReturn(List.of(
                colaborador(ANA, ANA_USUARIO, "ANA", BASE)));

        // 10 dos 31 dias de agosto: 4 * 10/31 = 1,29
        ApuracaoMetaPeriodo apuracao = useCase.apurar(INICIO_AGOSTO, INICIO_AGOSTO.plusDays(9), null, null, null);

        assertEquals(new BigDecimal("1.29"), duasCasas(apuracao.metaTotal()));
    }

    @Test
    @DisplayName("A meta individual deve substituir a meta da função no rateio")
    void metaIndividualSubstituiADaFuncao() {
        MetaInspecao individual = new MetaInspecao(TIPO_CAMPO, null, ANA, 10, PeriodicidadeMeta.MENSAL,
                LocalDate.of(2026, 1, 1), null, "Exceção");
        when(metaRepository.findByAtivoTrue()).thenReturn(List.of(metaMensalDaFuncao(4), individual));
        when(colaboradorApi.listarAtivosPorFuncao(FUNCAO)).thenReturn(List.of(
                colaborador(ANA, ANA_USUARIO, "ANA", BASE),
                colaborador(BRUNO, BRUNO_USUARIO, "BRUNO", BASE)));
        when(colaboradorApi.buscarPorIdResponse(ANA))
                .thenReturn(Optional.of(colaborador(ANA, ANA_USUARIO, "ANA", BASE)));

        ApuracaoMetaPeriodo apuracao = useCase.apurar(INICIO_AGOSTO, FIM_AGOSTO, null, null, null);

        assertEquals(new BigDecimal("10.00"), duasCasas(apuracao.metaPorInspetor().get(ANA_USUARIO)));
        assertEquals(new BigDecimal("4.00"), duasCasas(apuracao.metaPorInspetor().get(BRUNO_USUARIO)));
        assertEquals(new BigDecimal("14.00"), duasCasas(apuracao.metaTotal()));
        assertEquals(2, apuracao.colaboradoresComMeta());
    }

    @Test
    @DisplayName("Colaborador com meta e sem usuário conta na meta total e é sinalizado")
    void colaboradorSemUsuarioESinalizado() {
        when(metaRepository.findByAtivoTrue()).thenReturn(List.of(metaMensalDaFuncao(4)));
        when(colaboradorApi.listarAtivosPorFuncao(FUNCAO)).thenReturn(List.of(
                colaborador(ANA, ANA_USUARIO, "ANA", BASE),
                colaborador(BRUNO, null, "BRUNO", BASE)));

        ApuracaoMetaPeriodo apuracao = useCase.apurar(INICIO_AGOSTO, FIM_AGOSTO, null, null, null);

        assertEquals(new BigDecimal("8.00"), duasCasas(apuracao.metaTotal()));
        assertEquals(1, apuracao.metaPorInspetor().size());
        assertEquals(2, apuracao.colaboradoresComMeta());
        assertEquals(1, apuracao.colaboradoresSemAcesso());
    }

    @Test
    @DisplayName("Filtros de inspetor e de base devem restringir a meta apurada")
    void filtrosRestringemAMeta() {
        when(metaRepository.findByAtivoTrue()).thenReturn(List.of(metaMensalDaFuncao(4)));
        when(colaboradorApi.listarAtivosPorFuncao(FUNCAO)).thenReturn(List.of(
                colaborador(ANA, ANA_USUARIO, "ANA", BASE),
                colaborador(BRUNO, BRUNO_USUARIO, "BRUNO", OUTRA_BASE)));

        ApuracaoMetaPeriodo porInspetor = useCase.apurar(INICIO_AGOSTO, FIM_AGOSTO, List.of(ANA_USUARIO), null, null);
        assertEquals(new BigDecimal("4.00"), duasCasas(porInspetor.metaTotal()));
        assertEquals(1, porInspetor.colaboradoresComMeta());

        ApuracaoMetaPeriodo porBase = useCase.apurar(INICIO_AGOSTO, FIM_AGOSTO, null, List.of(OUTRA_BASE), null);
        assertEquals(new BigDecimal("4.00"), duasCasas(porBase.metaTotal()));
        assertTrue(porBase.metaPorInspetor().containsKey(BRUNO_USUARIO));
    }

    @Test
    @DisplayName("Várias bases selecionadas devem somar as metas de todas elas")
    void multiplasBasesSomamAsMetas() {
        when(metaRepository.findByAtivoTrue()).thenReturn(List.of(metaMensalDaFuncao(4)));
        when(colaboradorApi.listarAtivosPorFuncao(FUNCAO)).thenReturn(List.of(
                colaborador(ANA, ANA_USUARIO, "ANA", BASE),
                colaborador(BRUNO, BRUNO_USUARIO, "BRUNO", OUTRA_BASE)));

        ApuracaoMetaPeriodo apuracao = useCase.apurar(
                INICIO_AGOSTO, FIM_AGOSTO, null, List.of(BASE, OUTRA_BASE), null);

        assertEquals(new BigDecimal("8.00"), duasCasas(apuracao.metaTotal()));
        assertEquals(2, apuracao.colaboradoresComMeta());
    }

    @Test
    @DisplayName("O centro de custo deve restringir a meta às bases vinculadas a ele")
    void centroDeCustoRestringeAMeta() {
        when(metaRepository.findByAtivoTrue()).thenReturn(List.of(metaMensalDaFuncao(4)));
        when(colaboradorApi.listarAtivosPorFuncao(FUNCAO)).thenReturn(List.of(
                colaborador(ANA, ANA_USUARIO, "ANA", BASE, CENTRO_NORTE),
                colaborador(BRUNO, BRUNO_USUARIO, "BRUNO", OUTRA_BASE, CENTRO_SUL)));

        ApuracaoMetaPeriodo apuracao = useCase.apurar(
                INICIO_AGOSTO, FIM_AGOSTO, null, null, List.of(CENTRO_NORTE));

        assertEquals(new BigDecimal("4.00"), duasCasas(apuracao.metaTotal()));
        assertEquals(1, apuracao.colaboradoresComMeta());
        assertTrue(apuracao.metaPorInspetor().containsKey(ANA_USUARIO));
    }

    @Test
    @DisplayName("Filtro por tipo de inspeção deve considerar apenas metas do tipo selecionado")
    void filtroPorTipoDeInspecao() {
        MetaInspecao metaCampo = new MetaInspecao(TIPO_CAMPO, FUNCAO, null, 4, PeriodicidadeMeta.MENSAL,
                LocalDate.of(2026, 1, 1), null, "Meta campo");
        MetaInspecao metaAlojamento = new MetaInspecao(TIPO_ALOJAMENTO, FUNCAO, null, 2, PeriodicidadeMeta.MENSAL,
                LocalDate.of(2026, 1, 1), null, "Meta alojamento");
        when(metaRepository.findByAtivoTrue()).thenReturn(List.of(metaCampo, metaAlojamento));
        when(colaboradorApi.listarAtivosPorFuncao(FUNCAO)).thenReturn(List.of(
                colaborador(ANA, ANA_USUARIO, "ANA", BASE)));

        ApuracaoMetaPeriodo apuracaoCampo = useCase.apurar(
                INICIO_AGOSTO, FIM_AGOSTO, null, null, null, List.of(TIPO_CAMPO));

        assertEquals(new BigDecimal("4.00"), duasCasas(apuracaoCampo.metaTotal()));

        ApuracaoMetaPeriodo apuracaoTodos = useCase.apurar(
                INICIO_AGOSTO, FIM_AGOSTO, null, null, null, null);
        assertEquals(new BigDecimal("4.00"), duasCasas(apuracaoTodos.metaTotal()), "Colaborador tem 2 metas de funcao somadas ou aplicadas");
    }

    @Test
    @DisplayName("Sem meta vigente no intervalo a apuração deve vir vazia")
    void semMetaVigenteApuracaoVazia() {
        when(metaRepository.findByAtivoTrue()).thenReturn(List.of(
                new MetaInspecao(TIPO_CAMPO, FUNCAO, null, 4, PeriodicidadeMeta.MENSAL,
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), "Encerrada")));

        ApuracaoMetaPeriodo apuracao = useCase.apurar(INICIO_AGOSTO, FIM_AGOSTO, null, null, null);

        assertFalse(apuracao.possuiMeta());
        assertEquals(0, apuracao.colaboradoresComMeta());
        assertTrue(apuracao.metaPorInspetor().isEmpty());
    }
}
