package com.nexo.app.modules.desconto.application.usecases;

import com.nexo.app.modules.desconto.application.dto.*;
import com.nexo.app.modules.desconto.domain.*;
import com.nexo.app.modules.desconto.infrastructure.persistence.DescontoRepository;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ContextoOrganizacional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * As três chamadas ao repositório acontecem sempre na mesma ordem — período,
 * janela anterior e histórico de pendências — e os testes se apoiam nisso ao
 * encadear os retornos.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardDescontoUseCasesTest {

    @Mock private DescontoRepository repository;
    @Mock private ColaboradorApi colaboradorApi;
    @Mock private UsuarioApi usuarioApi;

    @InjectMocks
    private DashboardDescontoUseCases useCases;

    private final UUID colaboradorA = UUID.randomUUID();
    private final UUID colaboradorB = UUID.randomUUID();
    private final UUID baseNorte = UUID.randomUUID();
    private final UUID baseSul = UUID.randomUUID();
    private final UUID solicitante = UUID.randomUUID();

    private final MotivoDesconto extravio = new MotivoDesconto("Extravio de material", 6);
    private final TipoEquipamento epi = new TipoEquipamento("EPI");
    private final TipoEquipamento ferramenta = new TipoEquipamento("Ferramenta");

    private final OffsetDateTime hoje = OffsetDateTime.now();

    @BeforeEach
    void cadastrosResolvidos() {
        ReflectionTestUtils.setField(extravio, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(epi, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(ferramenta, "id", UUID.randomUUID());

        Map<UUID, ContextoOrganizacional> contextos = new HashMap<>();
        contextos.put(colaboradorA, contexto(colaboradorA, "ANA SOUZA", baseNorte));
        contextos.put(colaboradorB, contexto(colaboradorB, "BRUNO LIMA", baseSul));
        when(colaboradorApi.buscarContextoOrganizacional(any())).thenReturn(contextos);
        when(colaboradorApi.contarAtivos(any(), any())).thenReturn(10L);
        when(colaboradorApi.buscarNomeCompletoPorUsuario(any())).thenReturn(Optional.of("CARLA GESTORA"));
        when(usuarioApi.buscarLoginsPorIds(any())).thenReturn(Map.of());
        when(repository.buscarIdsComAnexoAtivo(any())).thenReturn(List.of());
    }

    private ContextoOrganizacional contexto(UUID id, String nome, UUID idBase) {
        return new ContextoOrganizacional(id, nome, UUID.randomUUID(), "ELETRICISTA",
                idBase, idBase.equals(baseNorte) ? "Base Norte" : "Base Sul",
                UUID.randomUUID(), "Regional Metro", UUID.randomUUID(), "Manutenção", true);
    }

    // ------------------------------------------------------------------ //

    private Desconto termo(UUID colaborador, OffsetDateTime ocorrencia,
                           TipoEquipamento tipo, BigDecimal... valores) {
        Desconto desconto = new Desconto(colaborador, extravio, "Ocorrência registrada em campo",
                2, ocorrencia, solicitante);
        ReflectionTestUtils.setField(desconto, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(desconto, "dataCriacao", ocorrencia);
        for (BigDecimal valor : valores) {
            desconto.adicionarItem("Material", tipo, valor);
        }
        desconto.getItens().forEach(item -> ReflectionTestUtils.setField(item, "id", UUID.randomUUID()));
        return desconto;
    }

    private Desconto validado(Desconto desconto, BigDecimal valorValidado, OffsetDateTime quando) {
        desconto.validar(UUID.randomUUID(), valorValidado);
        ReflectionTestUtils.setField(desconto, "dataValidacao", quando);
        return desconto;
    }

    private Desconto baixado(Desconto desconto, BigDecimal valorValidado, BigDecimal valorBaixado,
                             OffsetDateTime validacao, OffsetDateTime baixa) {
        validado(desconto, valorValidado, validacao);
        desconto.baixar(UUID.randomUUID(), valorBaixado);
        ReflectionTestUtils.setField(desconto, "dataBaixa", baixa);
        return desconto;
    }

    private Desconto rejeitado(Desconto desconto) {
        desconto.rejeitar(UUID.randomUUID(), "Sem comprovação");
        return desconto;
    }

    @SuppressWarnings("unchecked")
    private void carregamentos(List<Desconto> atuais, List<Desconto> anteriores, List<Desconto> pendentes) {
        when(repository.findAll(any(Specification.class)))
                .thenReturn(atuais, anteriores, pendentes);
    }

    private DashboardDescontoFilter filtroDoMes() {
        return new DashboardDescontoFilter(hoje.minusDays(20), hoje, null, null,
                null, null, null, null, null, null, null, null, GranularidadeDashboard.DIA);
    }

    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("O valor efetivo respeita a hierarquia baixado > validado > sugerido")
    void valorEfetivoUsaOQueValeAgora() {
        Desconto baixadoAbaixoDoPedido = baixado(
                termo(colaboradorA, hoje.minusDays(5), epi, new BigDecimal("300.00")),
                new BigDecimal("250.00"), new BigDecimal("200.00"),
                hoje.minusDays(3), hoje.minusDays(1));
        Desconto aguardandoBaixa = validado(
                termo(colaboradorB, hoje.minusDays(4), epi, new BigDecimal("100.00")),
                new BigDecimal("80.00"), hoje.minusDays(2));

        carregamentos(List.of(baixadoAbaixoDoPedido, aguardandoBaixa), List.of(), List.of(aguardandoBaixa));

        ResumoFinanceiroDashboard financeiro = useCases.gerar(filtroDoMes()).financeiro();

        assertEquals(0, new BigDecimal("400.00").compareTo(financeiro.valorSolicitado()));
        assertEquals(0, new BigDecimal("330.00").compareTo(financeiro.valorValidado()));
        assertEquals(0, new BigDecimal("200.00").compareTo(financeiro.valorBaixado()));
        // Glosa: 400 pedidos nos que chegaram à validação, 330 reconhecidos.
        assertEquals(0, new BigDecimal("70.00").compareTo(financeiro.glosaValidacao()));
        // Ajuste na baixa: só o termo baixado entra — 250 validados, 200 descontados.
        assertEquals(0, new BigDecimal("50.00").compareTo(financeiro.ajusteBaixa()));
        // Em aberto usa o valor validado do que aguarda baixa, não o sugerido.
        assertEquals(0, new BigDecimal("80.00").compareTo(financeiro.valorEmAberto()));
        assertEquals(0, new BigDecimal("50.00").compareTo(financeiro.taxaRecuperacao()));
    }

    @Test
    @DisplayName("O funil conta o termo baixado também como validado")
    void funilNaoAlargaDaEsquerdaParaDireita() {
        Desconto concluido = baixado(termo(colaboradorA, hoje.minusDays(6), epi, new BigDecimal("100.00")),
                new BigDecimal("100.00"), new BigDecimal("100.00"), hoje.minusDays(5), hoje.minusDays(4));
        Desconto emValidacao = validado(termo(colaboradorA, hoje.minusDays(6), epi, new BigDecimal("50.00")),
                new BigDecimal("50.00"), hoje.minusDays(5));
        Desconto recusado = rejeitado(termo(colaboradorB, hoje.minusDays(6), epi, new BigDecimal("70.00")));
        Desconto novo = termo(colaboradorB, hoje.minusDays(2), epi, new BigDecimal("30.00"));

        carregamentos(List.of(concluido, emValidacao, recusado, novo), List.of(), List.of(emValidacao, novo));

        List<FunilEtapaItem> funil = useCases.gerar(filtroDoMes()).funil();

        assertEquals(4, funil.get(0).quantidade(), "topo é a coorte inteira");
        assertEquals(2, funil.get(1).quantidade(), "validados incluem o que já foi baixado");
        assertEquals(1, funil.get(2).quantidade());
        assertEquals(1, funil.get(3).quantidade(), "rejeitados entram como vazamento");
        assertNull(funil.get(0).percentualDaEtapaAnterior(), "o topo não tem etapa anterior");
        assertEquals(0, new BigDecimal("50.00").compareTo(funil.get(2).percentualDaEtapaAnterior()),
                "1 de 2 validados chegou à baixa");
    }

    @Test
    @DisplayName("A série devolve todos os baldes do período, inclusive os zerados")
    void serieNaoDeixaBuraco() {
        Desconto unico = termo(colaboradorA, hoje.minusDays(2), epi, new BigDecimal("10.00"));
        carregamentos(List.of(unico), List.of(), List.of(unico));

        DashboardDescontosResponse resposta = useCases.gerar(filtroDoMes());

        assertEquals(21, resposta.serieTemporal().size(), "20 dias de janela, bordas inclusivas");
        assertEquals(1, resposta.serieTemporal().stream()
                .filter(item -> item.solicitados() > 0).count());
        assertTrue(resposta.serieTemporal().stream().allMatch(item -> item.valorSolicitado() != null));
    }

    @Test
    @DisplayName("A idade da pendência conta desde a entrada na etapa atual, não desde a abertura")
    void envelhecimentoUsaAEtapaCorrente() {
        // Aberto há 60 dias, mas validado ontem: é uma pendência nova na fila da baixa.
        Desconto validadoOntem = validado(
                termo(colaboradorA, hoje.minusDays(60), epi, new BigDecimal("100.00")),
                new BigDecimal("100.00"), hoje.minusDays(1));
        // Aberto há 40 dias e nunca validado: envelheceu de verdade.
        Desconto paradoNaValidacao = termo(colaboradorB, hoje.minusDays(40), epi, new BigDecimal("100.00"));

        carregamentos(List.of(), List.of(), List.of(validadoOntem, paradoNaValidacao));

        List<FaixaEnvelhecimentoItem> faixas = useCases.gerar(filtroDoMes()).envelhecimento();

        assertEquals(1, faixaDe(faixas, "0-7").quantidade());
        assertEquals(1L, faixaDe(faixas, "0-7").porStatus().get(StatusDesconto.VALIDADO));
        assertEquals(1, faixaDe(faixas, "31-60").quantidade());
        assertEquals(1L, faixaDe(faixas, "31-60").porStatus().get(StatusDesconto.SOLICITADO));
    }

    private FaixaEnvelhecimentoItem faixaDe(List<FaixaEnvelhecimentoItem> faixas, String nome) {
        return faixas.stream().filter(faixa -> faixa.faixa().equals(nome)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("O tipo de equipamento conta itens e termos distintos separadamente")
    void tipoEquipamentoSeparaItemDeTermo() {
        Desconto tresEpis = termo(colaboradorA, hoje.minusDays(3), epi,
                new BigDecimal("10.00"), new BigDecimal("20.00"), new BigDecimal("30.00"));
        Desconto umaFerramenta = termo(colaboradorB, hoje.minusDays(3), ferramenta, new BigDecimal("40.00"));

        carregamentos(List.of(tresEpis, umaFerramenta), List.of(), List.of(tresEpis, umaFerramenta));

        List<TipoEquipamentoDescontoItem> tipos = useCases.gerar(filtroDoMes()).porTipoEquipamento();

        TipoEquipamentoDescontoItem deEpi = tipos.stream()
                .filter(item -> item.nome().equals("EPI")).findFirst().orElseThrow();
        assertEquals(3, deEpi.itens());
        assertEquals(1, deEpi.descontos(), "três itens, um único termo");
        assertEquals(0, new BigDecimal("60.00").compareTo(deEpi.valorItens()));
        assertEquals(0, new BigDecimal("20.00").compareTo(deEpi.valorMedioPorItem()));
    }

    @Test
    @DisplayName("O recorte por base operacional é aplicado sobre a estrutura do colaborador")
    void recorteOrganizacionalFiltraOsTermos() {
        Desconto doNorte = termo(colaboradorA, hoje.minusDays(3), epi, new BigDecimal("100.00"));
        Desconto doSul = termo(colaboradorB, hoje.minusDays(3), epi, new BigDecimal("500.00"));

        carregamentos(List.of(doNorte, doSul), List.of(), List.of(doNorte, doSul));

        DashboardDescontosResponse resposta = useCases.gerar(new DashboardDescontoFilter(
                hoje.minusDays(20), hoje, null, null, null, null, null, List.of(baseNorte), null, null, null, null,
                GranularidadeDashboard.DIA));

        assertEquals(1, resposta.resumo().totalDescontos());
        assertEquals(0, new BigDecimal("100.00").compareTo(resposta.financeiro().valorSolicitado()));
        assertEquals(1, resposta.porBaseOperacional().size());
        assertEquals("Base Norte", resposta.porBaseOperacional().getFirst().nome());
    }

    @Test
    @DisplayName("Termo cujo colaborador sumiu do cadastro é contado na qualidade dos dados")
    void colaboradorSemCadastroFicaVisivel() {
        Desconto orfao = termo(UUID.randomUUID(), hoje.minusDays(3), epi, new BigDecimal("100.00"));
        carregamentos(List.of(orfao), List.of(), List.of(orfao));

        DashboardDescontosResponse resposta = useCases.gerar(filtroDoMes());

        assertEquals(1, resposta.resumo().totalDescontos(), "sem recorte organizacional, ele continua no total");
        assertEquals(1, resposta.qualidade().colaboradoresNaoIdentificados());
        assertTrue(resposta.porBaseOperacional().isEmpty(), "sem estrutura, não entra no agrupamento");
    }

    @Test
    @DisplayName("Pendência parada além do limiar vira prioridade acionável")
    void pendenciaParadaViraPrioridade() {
        Desconto esquecido = termo(colaboradorA, hoje.minusDays(45), epi, new BigDecimal("100.00"));
        Desconto recente = termo(colaboradorB, hoje.minusDays(2), epi, new BigDecimal("100.00"));

        carregamentos(List.of(recente), List.of(), List.of(esquecido, recente));

        List<PrioridadeDescontoItem> prioridades = useCases.gerar(filtroDoMes()).prioridades();

        PrioridadeDescontoItem parada = prioridades.stream()
                .filter(item -> item.tipo().equals("VALIDACAO_PARADA")).findFirst().orElseThrow();
        assertEquals(SeveridadePrioridade.CRITICA, parada.severidade(), "passou de 30 dias");
        assertEquals(1, parada.amostraIds().size());
        assertTrue(parada.titulo().startsWith("1 termo(s)"));
    }

    @Test
    @DisplayName("Sem nenhum desfecho no período, as taxas retornam zero em vez de estourar")
    void denominadorZeroNaoQuebra() {
        carregamentos(List.of(), List.of(), List.of());

        DashboardDescontosResponse resposta = useCases.gerar(filtroDoMes());

        assertEquals(BigDecimal.ZERO.setScale(2), resposta.resumo().taxaAprovacao());
        assertEquals(BigDecimal.ZERO.setScale(2), resposta.financeiro().taxaRecuperacao());
        assertEquals(BigDecimal.ZERO.setScale(2), resposta.ciclo().medianaDiasAteValidacao());
        assertTrue(resposta.prioridades().isEmpty());
        assertEquals(4, resposta.porStatus().size(), "todos os status aparecem, mesmo zerados");
    }

    @Test
    @DisplayName("O ciclo mede cada etapa a partir da data gravada na transição")
    void cicloMedeCadaEtapa() {
        Desconto rapido = baixado(termo(colaboradorA, hoje.minusDays(10), epi, new BigDecimal("100.00")),
                new BigDecimal("100.00"), new BigDecimal("100.00"),
                hoje.minusDays(8), hoje.minusDays(7));
        Desconto lento = baixado(termo(colaboradorB, hoje.minusDays(10), epi, new BigDecimal("100.00")),
                new BigDecimal("100.00"), new BigDecimal("100.00"),
                hoje.minusDays(4), hoje.minusDays(1));

        carregamentos(List.of(rapido, lento), List.of(), List.of());

        CicloDescontoDashboard ciclo = useCases.gerar(filtroDoMes()).ciclo();

        assertEquals(2, ciclo.amostraValidacao());
        assertEquals(0, new BigDecimal("4.00").compareTo(ciclo.mediaDiasAteValidacao()), "2 e 6 dias");
        assertEquals(0, new BigDecimal("2.00").compareTo(ciclo.mediaDiasAteBaixa()), "1 e 3 dias");
        assertEquals(0, new BigDecimal("6.00").compareTo(ciclo.mediaDiasCicloTotal()), "3 e 9 dias");
    }
}
