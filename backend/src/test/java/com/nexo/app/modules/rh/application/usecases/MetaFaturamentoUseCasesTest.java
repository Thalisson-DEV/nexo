package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.CronogramaMetaFaturamento;
import com.nexo.app.modules.rh.application.dto.MetaFaturamentoRequest;
import com.nexo.app.modules.rh.application.dto.MetaFaturamentoResponse;
import com.nexo.app.modules.rh.application.mappers.MetaFaturamentoMapper;
import com.nexo.app.modules.rh.domain.Equipe;
import com.nexo.app.modules.rh.domain.MetaFaturamentoEquipe;
import com.nexo.app.modules.rh.infrastructure.persistence.EquipeRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.MetaFaturamentoEquipeRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.nexo.app.modules.rh.PeriodicidadeMetaFaturamento.CICLO_COMERCIAL;
import static com.nexo.app.modules.rh.PeriodicidadeMetaFaturamento.MENSAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetaFaturamentoUseCasesTest {

    private static final LocalDate CICLO_OUTUBRO = LocalDate.of(2026, 9, 26);
    private static final LocalDate CICLO_NOVEMBRO = LocalDate.of(2026, 10, 26);

    @Mock
    private MetaFaturamentoEquipeRepository repository;
    @Mock
    private EquipeRepository equipeRepository;
    @Mock
    private MetaFaturamentoMapper mapper;

    @InjectMocks
    private MetaFaturamentoUseCases useCases;

    private UUID idEquipe;
    private Equipe equipe;

    @BeforeEach
    void setUp() {
        idEquipe = UUID.randomUUID();
        equipe = mock(Equipe.class);
        lenient().when(equipe.getId()).thenReturn(idEquipe);
        lenient().when(equipe.getCodigo()).thenReturn("EQ-01");
        lenient().when(equipe.isAtivo()).thenReturn(true);
        lenient().when(equipeRepository.findAllById(any())).thenReturn(List.of(equipe));
        lenient().when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(mapper.toResponse(any())).thenAnswer(inv -> {
            MetaFaturamentoEquipe m = inv.getArgument(0);
            return new MetaFaturamentoResponse(m.getId(), m.getIdEquipe(), null, null, null, m.getValor(),
                    m.getPeriodicidade(), m.getVigenciaInicio(), m.getVigenciaFim(), m.getDescricao(), m.isAtivo(),
                    null, null, null, null);
        });
    }

    @Test
    @DisplayName("A vigência de uma meta por ciclo começa no dia 26 e termina no dia 25")
    void vigenciaAlinhadaAoCiclo() {
        assertThatThrownBy(() -> new MetaFaturamentoEquipe(idEquipe, new BigDecimal("20000"), CICLO_COMERCIAL,
                LocalDate.of(2026, 10, 1), null, null))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("dia 26");
        assertThatThrownBy(() -> new MetaFaturamentoEquipe(idEquipe, new BigDecimal("20000"), CICLO_COMERCIAL,
                CICLO_OUTUBRO, LocalDate.of(2026, 10, 31), null))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("dia 25");
        assertThatThrownBy(() -> new MetaFaturamentoEquipe(idEquipe, BigDecimal.ZERO, MENSAL,
                LocalDate.of(2026, 10, 1), null, null))
                .isInstanceOf(InvarianteVioladaException.class);
    }

    @Test
    @DisplayName("Cadastra uma meta para cada equipe informada")
    void cadastraEmLote() {
        UUID outra = UUID.randomUUID();
        Equipe outraEquipe = mock(Equipe.class);
        when(outraEquipe.getId()).thenReturn(outra);
        when(outraEquipe.isAtivo()).thenReturn(true);
        when(equipeRepository.findAllById(any())).thenReturn(List.of(equipe, outraEquipe));

        List<MetaFaturamentoResponse> criadas = useCases.criar(request(List.of(idEquipe, outra), false));

        assertThat(criadas).extracting(MetaFaturamentoResponse::idEquipe).containsExactly(idEquipe, outra);
        assertThat(criadas).allSatisfy(m -> assertThat(m.valor()).isEqualByComparingTo("23000.00"));
    }

    @Test
    @DisplayName("Rejeita vigência sobreposta à meta ativa da equipe")
    void rejeitaSobreposicao() {
        var atual = new MetaFaturamentoEquipe(idEquipe, new BigDecimal("20000"), CICLO_COMERCIAL, CICLO_OUTUBRO, null, null);
        when(repository.findByAtivoTrueAndIdEquipe(idEquipe)).thenReturn(List.of(atual));

        assertThatThrownBy(() -> useCases.criar(request(List.of(idEquipe), false)))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("EQ-01");
        verify(repository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("Substituir encerra a meta atual no fim do ciclo anterior e preserva o histórico")
    void substituiMetaVigente() {
        var atual = new MetaFaturamentoEquipe(idEquipe, new BigDecimal("20000"), CICLO_COMERCIAL, CICLO_OUTUBRO, null, null);
        when(repository.findByAtivoTrueAndIdEquipe(idEquipe)).thenReturn(List.of(atual));

        useCases.criar(request(List.of(idEquipe), true));

        assertThat(atual.getVigenciaFim()).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(atual.isAtivo()).isTrue();
    }

    @Test
    @DisplayName("O cronograma traz as metas ativas e equipes sem meta recebem cronograma vazio")
    void cronogramas() {
        UUID semMeta = UUID.randomUUID();
        var meta = new MetaFaturamentoEquipe(idEquipe, new BigDecimal("20000"), CICLO_COMERCIAL, CICLO_OUTUBRO, null, null);
        ReflectionTestUtils.setField(meta, "id", UUID.randomUUID());
        when(repository.findByAtivoTrueAndIdEquipeIn(any())).thenReturn(List.of(meta));

        var cronogramas = useCases.cronogramas(List.of(idEquipe, semMeta));

        assertThat(cronogramas.get(idEquipe).valorDaJanelaEm(LocalDate.of(2026, 10, 10))).isEqualByComparingTo("20000");
        assertThat(cronogramas.get(semMeta)).isEqualTo(CronogramaMetaFaturamento.vazio());
    }

    private static MetaFaturamentoRequest request(List<UUID> equipes, boolean substituir) {
        return new MetaFaturamentoRequest(equipes, new BigDecimal("23000"), CICLO_COMERCIAL,
                CICLO_NOVEMBRO, null, "Meta a partir de novembro", substituir);
    }
}
