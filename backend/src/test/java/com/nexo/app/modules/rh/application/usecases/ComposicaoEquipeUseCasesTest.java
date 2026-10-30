package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.ComposicaoEquipeApi;
import com.nexo.app.modules.rh.application.mappers.EquipeMapper;
import com.nexo.app.modules.rh.domain.*;
import com.nexo.app.modules.rh.infrastructure.persistence.ColaboradorRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.EquipeMembroRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.EquipeRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.VeiculoRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComposicaoEquipeUseCasesTest {

    @Mock
    private MetaFaturamentoUseCases metaFaturamentoUseCases;

    @Mock
    private EquipeRepository equipeRepository;
    @Mock
    private ColaboradorRepository colaboradorRepository;
    @Mock
    private EquipeMembroRepository equipeMembroRepository;
    @Mock
    private VeiculoRepository veiculoRepository;
    @Mock
    private EquipeMapper equipeMapper;

    private ComposicaoEquipeUseCases useCases;

    private ProcessoNegocio processo;
    private BaseOperacional base;
    private Funcao funcao;
    private Equipe equipe;
    private UUID idUsuarioLider;
    private Colaborador lider;
    private Colaborador parceiroAntigo;
    private Colaborador parceiroNovo;
    private Veiculo veiculoAtual;
    private Veiculo veiculoNovo;

    @BeforeEach
    void setUp() {
        MembroEquipeUseCase membroEquipeUseCase = new MembroEquipeUseCase(
                equipeRepository, colaboradorRepository, equipeMembroRepository, equipeMapper);
        useCases = new ComposicaoEquipeUseCases(
                equipeRepository, colaboradorRepository, equipeMembroRepository, veiculoRepository, membroEquipeUseCase,
                metaFaturamentoUseCases);
        lenient().when(metaFaturamentoUseCases.metaVigente(any()))
                .thenReturn(new MetaFaturamentoUseCases.MetaVigente(null, null));

        processo = comId(new ProcessoNegocio("COMERCIAL"));
        CentroFaturamento centro = comId(new CentroFaturamento("COMERCIAL"));
        base = comId(new BaseOperacional("BASE SUL", "BA", "Rua Central, 100", centro));
        funcao = comId(new Funcao("ELETRICISTA", "Eletricista"));
        veiculoAtual = comId(new Veiculo("ABC1234", "Caminhão", "Ford", base));
        veiculoNovo = comId(new Veiculo("XYZ9876", "Picape", "Fiat", base));

        Colaborador colabCoord = colaborador("Carlos Silva", "111.222.333-44");
        Colaborador colabSup = colaborador("Marcos Oliveira", "222.333.444-55");
        Coordenador coordenador = comId(new Coordenador(colabCoord, LocalDate.now(), null));
        Supervisor supervisor = comId(new Supervisor(colabSup, coordenador, LocalDate.now(), null));

        lider = colaborador("Joao Santos", "333.444.555-66");
        idUsuarioLider = UUID.randomUUID();
        ReflectionTestUtils.setField(lider, "idUsuario", idUsuarioLider);
        parceiroAntigo = colaborador("Pedro Lima", "444.555.666-77");
        parceiroNovo = colaborador("Ana Souza", "555.666.777-88");

        equipe = comId(new Equipe("EQ-01", comId(new TipoEquipe("LINHA VIVA")), base, veiculoAtual,
                coordenador, supervisor, processo));
        equipe.alocarMembro(lider, EquipeMembro.FuncaoMembro.RESPONSAVEL);
        equipe.alocarMembro(parceiroAntigo, EquipeMembro.FuncaoMembro.PARCEIRO);

        lenient().when(equipeRepository.findById(equipe.getId())).thenReturn(Optional.of(equipe));
        lenient().when(equipeRepository.save(any(Equipe.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Responsável troca o veículo e os parceiros na abertura do turno")
    void responsavelAtualizaVeiculoEParceiros() {
        when(veiculoRepository.findById(veiculoNovo.getId())).thenReturn(Optional.of(veiculoNovo));
        when(colaboradorRepository.findById(parceiroNovo.getId())).thenReturn(Optional.of(parceiroNovo));
        when(equipeMembroRepository.findVincuoAtivoPorColaborador(parceiroNovo.getId())).thenReturn(Optional.empty());

        ComposicaoEquipeApi.ComposicaoEquipe composicao = useCases.atualizarPeloResponsavel(
                equipe.getId(), idUsuarioLider, veiculoNovo.getId(), List.of(parceiroNovo.getId()));

        assertThat(composicao.veiculo().placa()).isEqualTo("XYZ9876");
        assertThat(composicao.membros())
                .extracting(ComposicaoEquipeApi.MembroComposicao::idColaborador)
                .containsExactlyInAnyOrder(lider.getId(), parceiroNovo.getId());
        assertThat(composicao.membros().get(0).responsavel()).isTrue();
    }

    @Test
    @DisplayName("O responsável nunca é removido, mesmo com a lista de parceiros vazia")
    void responsavelPermaneceComListaVazia() {
        ComposicaoEquipeApi.ComposicaoEquipe composicao = useCases.atualizarPeloResponsavel(
                equipe.getId(), idUsuarioLider, null, List.of());

        assertThat(composicao.membros())
                .extracting(ComposicaoEquipeApi.MembroComposicao::idColaborador)
                .containsExactly(lider.getId());
        assertThat(composicao.veiculo().placa()).isEqualTo("ABC1234");
    }

    @Test
    @DisplayName("Quem não é o responsável não pode alterar a composição")
    void naoResponsavelNaoAltera() {
        assertThatThrownBy(() -> useCases.atualizarPeloResponsavel(
                equipe.getId(), UUID.randomUUID(), veiculoNovo.getId(), List.of()))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("responsável");
        verify(equipeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Composição expõe a meta da janela vigente vinda do cadastro de metas")
    void composicaoExpoeMeta() {
        when(metaFaturamentoUseCases.metaVigente(equipe.getId()))
                .thenReturn(new MetaFaturamentoUseCases.MetaVigente(new BigDecimal("32000.00"), new BigDecimal("1454.55")));

        assertThat(useCases.buscarComposicao(equipe.getId()))
                .get()
                .satisfies(c -> {
                    assertThat(c.metaFaturamentoMensal()).isEqualByComparingTo("32000.00");
                    assertThat(c.metaFaturamentoDiaria()).isEqualByComparingTo("1454.55");
                });
    }

    private Colaborador colaborador(String nome, String cpf) {
        String[] partes = nome.split(" ");
        return comId(new Colaborador(partes[0], partes[1], nome, cpf, processo, base, funcao, LocalDate.now()));
    }

    private static <T> T comId(T entidade) {
        ReflectionTestUtils.setField(entidade, "id", UUID.randomUUID());
        return entidade;
    }
}
