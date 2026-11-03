package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.application.dto.PlanejamentoObraRequests;
import com.nexo.app.modules.obras.application.dto.PlanejamentoObraResponse;
import com.nexo.app.modules.obras.domain.ItemPlanejado;
import com.nexo.app.modules.obras.domain.KitAplicado;
import com.nexo.app.modules.obras.domain.KitConstrutivo;
import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.obras.domain.OperacaoItemKit;
import com.nexo.app.modules.obras.domain.PontoPlanejamento;
import com.nexo.app.modules.obras.domain.StatusItemPlanejado;
import com.nexo.app.modules.obras.domain.TipoItemKit;
import com.nexo.app.modules.obras.domain.VaoPlanejamento;
import com.nexo.app.modules.obras.infrastructure.persistence.AnexoObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.AnotacaoPlanejamentoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.KitAplicadoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.PlanejamentoObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.PontoPlanejamentoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.VaoPlanejamentoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ProgramacaoRepository;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanejamentoObraUseCasesTest {

    private static final UUID OBRA = UUID.randomUUID();
    private static final UUID CONTRATO = UUID.randomUUID();
    private static final UUID ACAO = UUID.randomUUID();

    @Mock private ObraRepository obraRepository;
    @Mock private AnexoObraRepository anexoRepository;
    @Mock private AnexoObraUseCases anexoUseCases;
    @Mock private PlanejamentoObraRepository planejamentoRepository;
    @Mock private PontoPlanejamentoRepository pontoRepository;
    @Mock private VaoPlanejamentoRepository vaoRepository;
    @Mock private KitAplicadoRepository kitAplicadoRepository;
    @Mock private AnotacaoPlanejamentoRepository anotacaoRepository;
    @Mock private KitConstrutivoUseCases kitUseCases;
    @Mock private ProgramacaoRepository programacaoRepository;
    @Mock private EquipeApi equipeApi;

    @InjectMocks private PlanejamentoObraUseCases useCases;

    @BeforeEach
    void obra() {
        Obra obra = mock(Obra.class);
        lenient().when(obra.getId()).thenReturn(OBRA);
        lenient().when(obraRepository.findById(OBRA)).thenReturn(Optional.of(obra));
        lenient().when(planejamentoRepository.findByIdObraAndAtivoTrue(OBRA)).thenReturn(Optional.empty());
    }

    private static PontoPlanejamento ponto(String numero, long versao) {
        PontoPlanejamento ponto = new PontoPlanejamento(OBRA, numero, 1, BigDecimal.ZERO, BigDecimal.ZERO, null);
        ReflectionTestUtils.setField(ponto, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(ponto, "versao", versao);
        return ponto;
    }

    private static KitAplicado kitPendente(UUID idPonto, String quantidade) {
        KitConstrutivo catalogo = new KitConstrutivo(CONTRATO, "K1", "Kit", null, Set.of());
        return KitAplicado.noPonto(OBRA, idPonto, catalogo, OperacaoItemKit.INSTALAR, null,
                List.of(new KitAplicado.ModeloItem(TipoItemKit.SERVICO, ACAO, "S1", "SERVICO", OperacaoItemKit.INSTALAR,
                        "UD", new BigDecimal(quantidade))));
    }

    @Test
    void numeroDePontoRepetidoERecusado() {
        when(pontoRepository.existsByIdObraAndNumeroAndAtivoTrue(OBRA, "4")).thenReturn(true);

        assertThatThrownBy(() -> useCases.adicionarPonto(OBRA, new PlanejamentoObraRequests.NovoPonto(
                " 4 ", 1, BigDecimal.ZERO, BigDecimal.ZERO, null, null)))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("4");
        verify(pontoRepository, never()).save(any());
    }

    @Test
    void versaoDesatualizadaGeraConflitoSemGravar() {
        PontoPlanejamento ponto = ponto("4", 3);
        when(pontoRepository.findByIdAndIdObraAndAtivoTrue(ponto.getId(), OBRA)).thenReturn(Optional.of(ponto));

        assertThatThrownBy(() -> useCases.editarPonto(OBRA, ponto.getId(), new PlanejamentoObraRequests.EditarPonto(
                "4", "K051159", null, null, false, false, 2L)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(ponto.getBarramento()).isNull();
    }

    @Test
    void vaoDuplicadoERecusadoEmQualquerOrdem() {
        PontoPlanejamento a = ponto("2", 0);
        PontoPlanejamento b = ponto("4", 0);
        when(pontoRepository.findByIdAndIdObraAndAtivoTrue(a.getId(), OBRA)).thenReturn(Optional.of(a));
        when(pontoRepository.findByIdAndIdObraAndAtivoTrue(b.getId(), OBRA)).thenReturn(Optional.of(b));
        when(vaoRepository.findByIdObraAndAtivoTrue(OBRA))
                .thenReturn(List.of(new VaoPlanejamento(OBRA, a.getId(), b.getId(), null, null)));

        assertThatThrownBy(() -> useCases.adicionarVao(OBRA, new PlanejamentoObraRequests.NovoVao(b.getId(), a.getId(), null)))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("P4")
                .hasMessageContaining("P2");
    }

    @Test
    void excluirPontoComItemProgramadoEBloqueado() {
        PontoPlanejamento ponto = ponto("4", 0);
        KitAplicado kit = kitPendente(ponto.getId(), "1");
        ReflectionTestUtils.setField(kit.getItensAtivos().get(0), "status", StatusItemPlanejado.PROGRAMADO);
        when(pontoRepository.findByIdAndIdObraAndAtivoTrue(ponto.getId(), OBRA)).thenReturn(Optional.of(ponto));
        when(vaoRepository.findByIdObraAndAtivoTrue(OBRA)).thenReturn(List.of());
        when(kitAplicadoRepository.findByIdPontoAndAtivoTrue(ponto.getId())).thenReturn(List.of(kit));

        assertThatThrownBy(() -> useCases.excluirPonto(OBRA, ponto.getId()))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("SERVICO");
        assertThat(ponto.isAtivo()).isTrue();
    }

    @Test
    void indicadoresSeparamPendenteProgramadoEExecutado() {
        UUID idPonto = UUID.randomUUID();
        KitAplicado pendente = kitPendente(idPonto, "2");
        KitAplicado programado = kitPendente(idPonto, "3");
        ReflectionTestUtils.setField(programado.getItensAtivos().get(0), "status", StatusItemPlanejado.PROGRAMADO);
        KitAplicado antes = kitPendente(idPonto, "5");
        antes.marcarExecutadoAntes(true);
        Map<UUID, Map<UUID, BigDecimal>> valores = Map.of(CONTRATO, Map.of(ACAO, BigDecimal.TEN));

        PlanejamentoObraResponse.Indicadores indicadores =
                PlanejamentoObraUseCases.indicadores(List.of(pendente, programado, antes), valores, 1, 2);

        assertThat(indicadores.valorPendente()).isEqualByComparingTo("20");
        assertThat(indicadores.valorProgramado()).isEqualByComparingTo("30");
        assertThat(indicadores.valorExecutado()).isEqualByComparingTo("50");
        assertThat(indicadores.valorTotal()).isEqualByComparingTo("100");
        assertThat(indicadores.percentualExecutado()).isEqualByComparingTo("50");
        assertThat(indicadores.pontosSemKit()).isEqualTo(1);
        assertThat(indicadores.vaosSemKit()).isEqualTo(2);
    }

    @Test
    void servicoSemValorFicaForaDaSomaEEContado() {
        KitAplicado kit = kitPendente(UUID.randomUUID(), "2");
        ItemPlanejado item = kit.getItensAtivos().get(0);
        assertThat(item.getStatus()).isEqualTo(StatusItemPlanejado.PENDENTE);

        PlanejamentoObraResponse.Indicadores indicadores =
                PlanejamentoObraUseCases.indicadores(List.of(kit), Map.of(), 0, 0);

        assertThat(indicadores.valorTotal()).isEqualByComparingTo("0");
        assertThat(indicadores.servicosSemValor()).isEqualTo(1);
    }

    @Test
    void projetoMestrePrecisaSerPdf() {
        assertThatThrownBy(() -> useCases.enviarProjetoMestre(OBRA, UUID.randomUUID(), null))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("PDF");
        verify(anexoUseCases, never()).anexar(any(), any(), anyString(), any());
    }
}
