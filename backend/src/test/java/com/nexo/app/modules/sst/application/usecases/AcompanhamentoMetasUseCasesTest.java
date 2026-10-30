package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.rh.BaseOperacionalResponse;
import com.nexo.app.modules.rh.CentroFaturamentoResponse;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ColaboradorResponse;
import com.nexo.app.modules.rh.FuncaoResponse;
import com.nexo.app.modules.rh.ProcessoNegocioResponse;
import com.nexo.app.modules.sst.application.dto.AcompanhamentoMetaFilter;
import com.nexo.app.modules.sst.application.dto.AcompanhamentoMetaItem;
import com.nexo.app.modules.sst.application.dto.AcompanhamentoMetasResponse;
import com.nexo.app.modules.sst.domain.EscopoMeta;
import com.nexo.app.modules.sst.domain.MetaInspecao;
import com.nexo.app.modules.sst.domain.OrdenacaoAcompanhamentoMeta;
import com.nexo.app.modules.sst.domain.PeriodicidadeMeta;
import com.nexo.app.modules.sst.domain.StatusMeta;
import com.nexo.app.modules.sst.infrastructure.persistence.InspecaoSegurancaRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.InspecaoSegurancaRepository.RealizacaoInspetorProjection;
import com.nexo.app.modules.sst.infrastructure.persistence.MetaInspecaoRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.TipoInspecaoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AcompanhamentoMetasUseCasesTest {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate REFERENCIA = LocalDate.of(2026, 8, 10);
    private static final LocalDate VIGENCIA = LocalDate.of(2026, 1, 1);

    private static final UUID TIPO_CAMPO = UUID.randomUUID();
    private static final UUID FUNCAO = UUID.randomUUID();
    private static final UUID BASE = UUID.randomUUID();
    private static final UUID BASE_SANTAREM = UUID.randomUUID();
    private static final UUID BASE_CURITIBA = UUID.randomUUID();
    private static final UUID CENTRO_NORTE = UUID.randomUUID();
    private static final UUID CENTRO_SUL = UUID.randomUUID();
    private static final UUID MANUTENCAO = UUID.randomUUID();
    private static final UUID COMERCIAL = UUID.randomUUID();

    private static final UUID ANA = UUID.randomUUID();
    private static final UUID ANA_USUARIO = UUID.randomUUID();
    private static final UUID BRUNO = UUID.randomUUID();
    private static final UUID BRUNO_USUARIO = UUID.randomUUID();
    private static final UUID CARLA = UUID.randomUUID();
    private static final UUID CARLA_USUARIO = UUID.randomUUID();

    @Mock
    private MetaInspecaoRepository metaRepository;
    @Mock
    private TipoInspecaoRepository tipoInspecaoRepository;
    @Mock
    private InspecaoSegurancaRepository inspecaoRepository;
    @Mock
    private ColaboradorApi colaboradorApi;

    @InjectMocks
    private AcompanhamentoMetasUseCases useCases;

    private MetaInspecao metaDaFuncao(int quantidade) {
        return new MetaInspecao(TIPO_CAMPO, FUNCAO, null, quantidade, PeriodicidadeMeta.MENSAL, VIGENCIA, null, "Meta mensal");
    }

    private ColaboradorResponse colaborador(UUID id, UUID idUsuario, String nome) {
        return colaborador(id, idUsuario, nome, BASE, null, null);
    }

    private ColaboradorResponse colaborador(UUID id, UUID idUsuario, String nome,
                                            UUID idBase, UUID idCentroCusto, UUID idProcesso) {
        CentroFaturamentoResponse centro = idCentroCusto == null ? null
                : new CentroFaturamentoResponse(idCentroCusto,
                        CENTRO_NORTE.equals(idCentroCusto) ? "CENTRO-NORTE" : "CENTRO-SUL", true);
        ProcessoNegocioResponse processo = idProcesso == null ? null
                : new ProcessoNegocioResponse(idProcesso,
                        COMERCIAL.equals(idProcesso) ? "COMERCIAL" : "MANUTENCAO", true);
        return new ColaboradorResponse(id, idUsuario, nome, "SILVA", nome + " SILVA", "00000000000",
                processo, new BaseOperacionalResponse(
                        idBase, "BASE " + nome, null, null, centro, null, null, null, true),
                new FuncaoResponse(FUNCAO, "ENCARREGADO", null, true), LocalDate.of(2020, 1, 1), true);
    }

    private RealizacaoInspetorProjection realizacao(UUID inspetor, LocalDate data) {
        OffsetDateTime momento = data.atStartOfDay(ZONA).plusHours(9).toOffsetDateTime();
        return new RealizacaoInspetorProjection() {
            @Override
            public UUID getIdInspetor() {
                return inspetor;
            }

            @Override
            public OffsetDateTime getDataRealizacao() {
                return momento;
            }

            @Override
            public UUID getIdTipoInspecao() {
                return TIPO_CAMPO;
            }
        };
    }

    private AcompanhamentoMetaItem item(AcompanhamentoMetasResponse response, UUID idColaborador) {
        return response.itens().stream()
                .filter(i -> i.idColaborador().equals(idColaborador))
                .findFirst().orElseThrow();
    }

    private AcompanhamentoMetasResponse acompanhar() {
        return useCases.acompanhar(filtro().construir());
    }

    private FiltroBuilder filtro() {
        return new FiltroBuilder(REFERENCIA);
    }

    /** Monta o filtro sem repetir a lista de nulos posicionais em cada teste. */
    private static final class FiltroBuilder {
        private final LocalDate referencia;
        private List<UUID> tipos;
        private List<UUID> bases;
        private List<UUID> centros;
        private List<UUID> processos;
        private List<StatusMeta> status;
        private OrdenacaoAcompanhamentoMeta ordenarPor;
        private Sort.Direction direcao;

        private FiltroBuilder(LocalDate referencia) {
            this.referencia = referencia;
        }

        FiltroBuilder tipos(UUID... ids) {
            this.tipos = List.of(ids);
            return this;
        }

        FiltroBuilder bases(UUID... ids) {
            this.bases = List.of(ids);
            return this;
        }

        FiltroBuilder centros(UUID... ids) {
            this.centros = List.of(ids);
            return this;
        }

        FiltroBuilder processos(UUID... ids) {
            this.processos = List.of(ids);
            return this;
        }

        FiltroBuilder status(StatusMeta... valores) {
            this.status = List.of(valores);
            return this;
        }

        FiltroBuilder ordenacao(OrdenacaoAcompanhamentoMeta ordenarPor, Sort.Direction direcao) {
            this.ordenarPor = ordenarPor;
            this.direcao = direcao;
            return this;
        }

        AcompanhamentoMetaFilter construir() {
            return new AcompanhamentoMetaFilter(referencia, tipos, null, null, bases, centros,
                    processos, status, ordenarPor, direcao);
        }
    }

    @Test
    @DisplayName("Deve expandir a meta da função e comparar o realizado de cada colaborador na janela")
    void deveApurarMetaDaFuncao() {
        when(metaRepository.findByAtivoTrue()).thenReturn(List.of(metaDaFuncao(3)));
        when(colaboradorApi.listarAtivosPorFuncao(FUNCAO)).thenReturn(List.of(
                colaborador(ANA, ANA_USUARIO, "ANA"),
                colaborador(BRUNO, BRUNO_USUARIO, "BRUNO")));
        when(inspecaoRepository.listarRealizacoesPorInspetor(anyCollection(), any(), any())).thenReturn(List.of(
                realizacao(ANA_USUARIO, LocalDate.of(2026, 8, 3)),
                realizacao(ANA_USUARIO, LocalDate.of(2026, 8, 5)),
                realizacao(ANA_USUARIO, LocalDate.of(2026, 8, 9)),
                realizacao(BRUNO_USUARIO, LocalDate.of(2026, 8, 4))));

        AcompanhamentoMetasResponse response = acompanhar();

        assertEquals(2, response.totalColaboradores());
        assertEquals(1, response.atingiram());
        assertEquals(1, response.emAndamento());
        assertEquals(6, response.somaMetas());
        assertEquals(4, response.somaRealizado());

        AcompanhamentoMetaItem ana = item(response, ANA);
        assertEquals(EscopoMeta.FUNCAO, ana.origem());
        assertEquals(LocalDate.of(2026, 8, 1), ana.janelaInicio());
        assertEquals(LocalDate.of(2026, 8, 31), ana.janelaFim());
        assertEquals(3, ana.meta());
        assertEquals(3, ana.realizado());
        assertEquals(0, ana.saldo());
        assertEquals(StatusMeta.ATINGIDA, ana.status());

        AcompanhamentoMetaItem bruno = item(response, BRUNO);
        assertEquals(1, bruno.realizado());
        assertEquals(2, bruno.saldo());
        assertEquals(StatusMeta.EM_ANDAMENTO, bruno.status());
        assertEquals(21, bruno.diasRestantes());
    }

    @Test
    @DisplayName("Inspeções fora da janela de apuração não devem contar")
    void naoDeveContarForaDaJanela() {
        when(metaRepository.findByAtivoTrue()).thenReturn(List.of(metaDaFuncao(2)));
        when(colaboradorApi.listarAtivosPorFuncao(FUNCAO)).thenReturn(List.of(colaborador(ANA, ANA_USUARIO, "ANA")));
        when(inspecaoRepository.listarRealizacoesPorInspetor(anyCollection(), any(), any())).thenReturn(List.of(
                realizacao(ANA_USUARIO, LocalDate.of(2026, 7, 31)),
                realizacao(ANA_USUARIO, LocalDate.of(2026, 8, 2))));

        AcompanhamentoMetaItem ana = item(acompanhar(), ANA);

        assertEquals(1, ana.realizado());
        assertEquals(StatusMeta.EM_ANDAMENTO, ana.status());
    }

    @Test
    @DisplayName("A meta individual do colaborador deve prevalecer sobre a meta da função")
    void metaIndividualDevePrevalecer() {
        MetaInspecao individual = new MetaInspecao(TIPO_CAMPO, null, ANA, 1, PeriodicidadeMeta.MENSAL, VIGENCIA, null, "Exceção");
        when(metaRepository.findByAtivoTrue()).thenReturn(List.of(metaDaFuncao(5), individual));
        when(colaboradorApi.listarAtivosPorFuncao(FUNCAO)).thenReturn(List.of(
                colaborador(ANA, ANA_USUARIO, "ANA"),
                colaborador(BRUNO, BRUNO_USUARIO, "BRUNO")));
        when(colaboradorApi.buscarPorIdResponse(ANA)).thenReturn(Optional.of(colaborador(ANA, ANA_USUARIO, "ANA")));
        when(inspecaoRepository.listarRealizacoesPorInspetor(anyCollection(), any(), any())).thenReturn(List.of(
                realizacao(ANA_USUARIO, LocalDate.of(2026, 8, 3))));

        AcompanhamentoMetasResponse response = acompanhar();

        AcompanhamentoMetaItem ana = item(response, ANA);
        assertEquals(EscopoMeta.COLABORADOR, ana.origem());
        assertEquals(1, ana.meta());
        assertEquals(StatusMeta.ATINGIDA, ana.status());

        AcompanhamentoMetaItem bruno = item(response, BRUNO);
        assertEquals(EscopoMeta.FUNCAO, bruno.origem());
        assertEquals(5, bruno.meta());
        assertEquals(2, response.totalColaboradores(), "o colaborador não pode aparecer duas vezes");
    }

    @Test
    @DisplayName("Janela encerrada sem cumprir a meta deve ficar como NAO_ATINGIDA")
    void janelaEncerradaDeveMarcarNaoAtingida() {
        when(metaRepository.findByAtivoTrue()).thenReturn(List.of(
                new MetaInspecao(TIPO_CAMPO, FUNCAO, null, 2, PeriodicidadeMeta.PERSONALIZADO,
                        LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 31), "Campanha")));
        when(colaboradorApi.listarAtivosPorFuncao(FUNCAO)).thenReturn(List.of(colaborador(ANA, ANA_USUARIO, "ANA")));
        when(inspecaoRepository.listarRealizacoesPorInspetor(anyCollection(), any(), any())).thenReturn(List.of());

        AcompanhamentoMetaItem emAberto = item(acompanhar(), ANA);
        assertEquals(StatusMeta.EM_ANDAMENTO, emAberto.status());

        AcompanhamentoMetaItem encerrada = item(
                useCases.acompanhar(new FiltroBuilder(LocalDate.of(2026, 8, 31)).construir()),
                ANA);
        assertEquals(0, encerrada.diasRestantes());
        assertEquals(StatusMeta.EM_ANDAMENTO, encerrada.status(), "no último dia a janela ainda está aberta");
    }

    @Test
    @DisplayName("Colaborador sem usuário vinculado não pode lançar inspeções e fica sem realizado")
    void colaboradorSemUsuarioFicaZerado() {
        when(metaRepository.findByAtivoTrue()).thenReturn(List.of(metaDaFuncao(2)));
        when(colaboradorApi.listarAtivosPorFuncao(FUNCAO)).thenReturn(List.of(colaborador(ANA, null, "ANA")));

        AcompanhamentoMetaItem ana = item(acompanhar(), ANA);

        assertEquals(0, ana.realizado());
        assertEquals(2, ana.saldo());
        verify(inspecaoRepository, never()).listarRealizacoesPorInspetor(anyCollection(), any(), any());
    }

    @Test
    @DisplayName("Deve filtrar o acompanhamento por situação e por base operacional")
    void deveAplicarFiltros() {
        when(metaRepository.findByAtivoTrue()).thenReturn(List.of(metaDaFuncao(3)));
        when(colaboradorApi.listarAtivosPorFuncao(FUNCAO)).thenReturn(List.of(
                colaborador(ANA, ANA_USUARIO, "ANA"),
                colaborador(BRUNO, BRUNO_USUARIO, "BRUNO")));
        when(inspecaoRepository.listarRealizacoesPorInspetor(anyCollection(), any(), any())).thenReturn(List.of(
                realizacao(ANA_USUARIO, LocalDate.of(2026, 8, 3)),
                realizacao(ANA_USUARIO, LocalDate.of(2026, 8, 5)),
                realizacao(ANA_USUARIO, LocalDate.of(2026, 8, 9))));

        AcompanhamentoMetasResponse pendentes = useCases.acompanhar(
                filtro().status(StatusMeta.EM_ANDAMENTO).construir());
        assertEquals(1, pendentes.totalColaboradores());
        assertEquals(BRUNO, pendentes.itens().get(0).idColaborador());

        AcompanhamentoMetasResponse outraBase = useCases.acompanhar(
                filtro().bases(UUID.randomUUID()).construir());
        assertEquals(0, outraBase.totalColaboradores());
        assertTrue(outraBase.itens().isEmpty());
    }

    // =================================================================== //
    //  Múltipla seleção, novos recortes e ordenação
    // =================================================================== //

    /** Cenário com três colaboradores em bases, centros de custo e processos distintos. */
    private void tresColaboradoresEmEscoposDiferentes() {
        when(metaRepository.findByAtivoTrue()).thenReturn(List.of(metaDaFuncao(3)));
        when(colaboradorApi.listarAtivosPorFuncao(FUNCAO)).thenReturn(List.of(
                colaborador(ANA, ANA_USUARIO, "ANA", BASE, CENTRO_NORTE, MANUTENCAO),
                colaborador(BRUNO, BRUNO_USUARIO, "BRUNO", BASE_SANTAREM, CENTRO_NORTE, COMERCIAL),
                colaborador(CARLA, CARLA_USUARIO, "CARLA", BASE_CURITIBA, CENTRO_SUL, MANUTENCAO)));
        when(inspecaoRepository.listarRealizacoesPorInspetor(anyCollection(), any(), any())).thenReturn(List.of());
    }

    private List<UUID> idsDe(AcompanhamentoMetasResponse response) {
        return response.itens().stream().map(AcompanhamentoMetaItem::idColaborador).toList();
    }

    @Test
    @DisplayName("Deve filtrar as metas pelo tipo de inspeção informado")
    void deveFiltrarPorTipoDeInspecao() {
        UUID tipoVeicular = UUID.randomUUID();
        MetaInspecao metaCampo = new MetaInspecao(TIPO_CAMPO, FUNCAO, null, 3, PeriodicidadeMeta.MENSAL, VIGENCIA, null, "Meta Campo");
        MetaInspecao metaVeicular = new MetaInspecao(tipoVeicular, null, BRUNO, 2, PeriodicidadeMeta.MENSAL, VIGENCIA, null, "Meta Veicular");

        when(metaRepository.findByAtivoTrue()).thenReturn(List.of(metaCampo, metaVeicular));
        when(colaboradorApi.listarAtivosPorFuncao(FUNCAO)).thenReturn(List.of(colaborador(ANA, ANA_USUARIO, "ANA")));
        when(colaboradorApi.buscarPorIdResponse(BRUNO)).thenReturn(Optional.of(colaborador(BRUNO, BRUNO_USUARIO, "BRUNO")));
        when(inspecaoRepository.listarRealizacoesPorInspetor(anyCollection(), any(), any())).thenReturn(List.of());

        AcompanhamentoMetasResponse response = useCases.acompanhar(filtro().tipos(tipoVeicular).construir());

        assertThat(idsDe(response)).containsExactly(BRUNO);
    }

    @Test
    @DisplayName("Deve aceitar várias bases operacionais na mesma consulta")
    void deveFiltrarPorVariasBases() {
        tresColaboradoresEmEscoposDiferentes();

        AcompanhamentoMetasResponse response = useCases.acompanhar(
                filtro().bases(BASE, BASE_CURITIBA).construir());

        assertThat(idsDe(response)).containsExactlyInAnyOrder(ANA, CARLA);
    }

    @Test
    @DisplayName("O filtro por centro de custo alcança todas as bases vinculadas a ele")
    void deveFiltrarPorCentroDeCusto() {
        tresColaboradoresEmEscoposDiferentes();

        AcompanhamentoMetasResponse response = useCases.acompanhar(filtro().centros(CENTRO_NORTE).construir());

        assertThat(idsDe(response)).containsExactlyInAnyOrder(ANA, BRUNO);
    }

    @Test
    @DisplayName("Deve filtrar pelo processo de negócio do colaborador")
    void deveFiltrarPorProcesso() {
        tresColaboradoresEmEscoposDiferentes();

        AcompanhamentoMetasResponse response = useCases.acompanhar(filtro().processos(COMERCIAL).construir());

        assertThat(idsDe(response)).containsExactly(BRUNO);
    }

    @Test
    @DisplayName("Dimensões diferentes se combinam com E: centro de custo E processo")
    void deveCombinarCentroDeCustoComProcesso() {
        tresColaboradoresEmEscoposDiferentes();

        AcompanhamentoMetasResponse response = useCases.acompanhar(
                filtro().centros(CENTRO_NORTE).processos(MANUTENCAO).construir());

        assertThat(idsDe(response)).containsExactly(ANA);
    }

    @Test
    @DisplayName("Deve aceitar várias situações ao mesmo tempo")
    void deveFiltrarPorVariasSituacoes() {
        tresColaboradoresEmEscoposDiferentes();

        AcompanhamentoMetasResponse response = useCases.acompanhar(
                filtro().status(StatusMeta.EM_ANDAMENTO, StatusMeta.ATINGIDA).construir());

        assertThat(idsDe(response)).containsExactlyInAnyOrder(ANA, BRUNO, CARLA);
    }

    @Test
    @DisplayName("Seleção vazia ou filtro nulo não restringem nada")
    void selecaoVaziaNaoRestringe() {
        tresColaboradoresEmEscoposDiferentes();

        assertEquals(3, acompanhar().totalColaboradores());
        assertEquals(3, useCases.acompanhar(AcompanhamentoMetaFilter.vazio()).totalColaboradores());
        assertEquals(3, useCases.acompanhar(null).totalColaboradores());
    }

    @Test
    @DisplayName("Deve ordenar pelo critério e pela direção informados")
    void deveOrdenarPeloCriterioInformado() {
        tresColaboradoresEmEscoposDiferentes();

        AcompanhamentoMetasResponse crescente = useCases.acompanhar(filtro()
                .ordenacao(OrdenacaoAcompanhamentoMeta.COLABORADOR, Sort.Direction.ASC).construir());
        AcompanhamentoMetasResponse decrescente = useCases.acompanhar(filtro()
                .ordenacao(OrdenacaoAcompanhamentoMeta.COLABORADOR, Sort.Direction.DESC).construir());

        assertThat(idsDe(crescente)).containsExactly(ANA, BRUNO, CARLA);
        assertThat(idsDe(decrescente)).containsExactly(CARLA, BRUNO, ANA);
    }

    @Test
    @DisplayName("A ordenação por centro de custo desempata pelo nome do colaborador")
    void deveDesempatarPeloNomeDoColaborador() {
        tresColaboradoresEmEscoposDiferentes();

        AcompanhamentoMetasResponse response = useCases.acompanhar(filtro()
                .ordenacao(OrdenacaoAcompanhamentoMeta.CENTRO_CUSTO, Sort.Direction.ASC).construir());

        // CENTRO-NORTE antes de CENTRO-SUL e, dentro do primeiro, ANA antes de BRUNO.
        assertThat(idsDe(response)).containsExactly(ANA, BRUNO, CARLA);
    }

    @Test
    @DisplayName("O item deve expor o centro de custo e o processo do colaborador")
    void itemDeveExporCentroDeCustoEProcesso() {
        tresColaboradoresEmEscoposDiferentes();

        AcompanhamentoMetaItem bruno = item(acompanhar(), BRUNO);

        assertEquals(CENTRO_NORTE, bruno.idCentroCusto());
        assertEquals("CENTRO-NORTE", bruno.nomeCentroCusto());
        assertEquals(COMERCIAL, bruno.idProcesso());
        assertEquals("COMERCIAL", bruno.nomeProcesso());
    }
}
