package com.nexo.app.modules.rh.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EquipeTest {

    private TipoEquipe tipoEquipe;
    private CentroFaturamento centroFaturamento;
    private BaseOperacional baseOperacional;
    private Veiculo veiculo;
    private ProcessoNegocio processo;
    private Funcao funcao;
    private Colaborador colabCoordenador;
    private Colaborador colabSupervisor;
    private Colaborador colabMembro;
    private Coordenador coordenador;
    private Supervisor supervisor;

    @BeforeEach
    void setUp() {
        processo = new ProcessoNegocio("COMERCIAL");
        ReflectionTestUtils.setField(processo, "id", UUID.randomUUID());

        centroFaturamento = new CentroFaturamento("COMERCIAL");
        ReflectionTestUtils.setField(centroFaturamento, "id", UUID.randomUUID());

        baseOperacional = new BaseOperacional("BASE SUL", "BA", "Rua Central, 100", centroFaturamento);
        ReflectionTestUtils.setField(baseOperacional, "id", UUID.randomUUID());

        tipoEquipe = new TipoEquipe("LINHA VIVA");
        ReflectionTestUtils.setField(tipoEquipe, "id", UUID.randomUUID());

        veiculo = new Veiculo("ABC1234", "Caminhão", "Ford", baseOperacional);
        ReflectionTestUtils.setField(veiculo, "id", UUID.randomUUID());

        funcao = new Funcao("ELETRICISTA", "Eletricista");
        ReflectionTestUtils.setField(funcao, "id", UUID.randomUUID());

        colabCoordenador = new Colaborador("Carlos", "Silva", "Carlos Silva", "111.222.333-44", processo, baseOperacional, funcao, LocalDate.now());
        ReflectionTestUtils.setField(colabCoordenador, "id", UUID.randomUUID());

        colabSupervisor = new Colaborador("Marcos", "Oliveira", "Marcos Oliveira", "222.333.444-55", processo, baseOperacional, funcao, LocalDate.now());
        ReflectionTestUtils.setField(colabSupervisor, "id", UUID.randomUUID());

        colabMembro = new Colaborador("Joao", "Santos", "Joao Santos", "333.444.555-66", processo, baseOperacional, funcao, LocalDate.now());
        ReflectionTestUtils.setField(colabMembro, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(colabMembro, "idUsuario", UUID.randomUUID());

        coordenador = new Coordenador(colabCoordenador, LocalDate.now(), null);
        ReflectionTestUtils.setField(coordenador, "id", UUID.randomUUID());

        supervisor = new Supervisor(colabSupervisor, coordenador, LocalDate.now(), null);
        ReflectionTestUtils.setField(supervisor, "id", UUID.randomUUID());
    }

    @Test
    @DisplayName("Deve instanciar equipe com sucesso quando coordenador e supervisor forem válidos")
    void deveInstanciarEquipeComSucesso() {
        Equipe equipe = new Equipe("EQ-01", tipoEquipe, baseOperacional, veiculo, coordenador, supervisor, processo);

        assertThat(equipe.getCodigo()).isEqualTo("EQ-01");
        assertThat(equipe.getCoordenador()).isEqualTo(coordenador);
        assertThat(equipe.getSupervisor()).isEqualTo(supervisor);
        assertThat(equipe.getTipoEquipe()).isEqualTo(tipoEquipe);
        assertThat(equipe.isAtivo()).isTrue();
    }

    @Test
    @DisplayName("Deve lançar exceção ao instanciar equipe com coordenador inativo")
    void deveLancarExcecaoComCoordenadorInativo() {
        coordenador.desativar();

        assertThatThrownBy(() -> new Equipe("EQ-01", tipoEquipe, baseOperacional, veiculo, coordenador, supervisor, processo))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("Não é possível vincular um coordenador desativado");
    }

    @Test
    @DisplayName("Deve lançar exceção ao instanciar equipe com supervisor inativo")
    void deveLancarExcecaoComSupervisorInativo() {
        supervisor.desativar();

        assertThatThrownBy(() -> new Equipe("EQ-01", tipoEquipe, baseOperacional, veiculo, coordenador, supervisor, processo))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("Não é possível vincular um supervisor desativado");
    }

    @Test
    @DisplayName("Deve lançar exceção quando o supervisor responde a outro coordenador")
    void deveLancarExcecaoQuandoSupervisorRespondeAOutroCoordenador() {
        Colaborador outroColabCoord = new Colaborador("Outro", "Coord", "Outro Coord", "444.555.666-77", processo, baseOperacional, funcao, LocalDate.now());
        ReflectionTestUtils.setField(outroColabCoord, "id", UUID.randomUUID());

        Coordenador outroCoordenador = new Coordenador(outroColabCoord, LocalDate.now(), null);
        ReflectionTestUtils.setField(outroCoordenador, "id", UUID.randomUUID());

        Supervisor supervisorDeOutro = new Supervisor(colabSupervisor, outroCoordenador, LocalDate.now(), null);
        ReflectionTestUtils.setField(supervisorDeOutro, "id", UUID.randomUUID());

        assertThatThrownBy(() -> new Equipe("EQ-01", tipoEquipe, baseOperacional, veiculo, coordenador, supervisorDeOutro, processo))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("O supervisor selecionado responde a um coordenador diferente do coordenador da equipe.");
    }

    @Test
    @DisplayName("Deve lançar exceção ao tentar alocar como membro o colaborador coordenador da equipe")
    void deveLancarExcecaoAoAlocarCoordenadorComoMembro() {
        Equipe equipe = new Equipe("EQ-01", tipoEquipe, baseOperacional, veiculo, coordenador, supervisor, processo);

        assertThatThrownBy(() -> equipe.alocarMembro(colabCoordenador, EquipeMembro.FuncaoMembro.PARCEIRO))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("O coordenador da equipe não pode ser alocado como seu próprio membro.");
    }

    @Test
    @DisplayName("Deve lançar exceção ao tentar alocar como membro o colaborador supervisor da equipe")
    void deveLancarExcecaoAoAlocarSupervisorComoMembro() {
        Equipe equipe = new Equipe("EQ-01", tipoEquipe, baseOperacional, veiculo, coordenador, supervisor, processo);

        assertThatThrownBy(() -> equipe.alocarMembro(colabSupervisor, EquipeMembro.FuncaoMembro.PARCEIRO))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("O supervisor da equipe não pode ser alocado como seu próprio membro.");
    }

    @Test
    @DisplayName("Deve alocar membro regular com sucesso e vincular idUsuario se for RESPONSAVEL")
    void deveAlocarMembroResponsavelComSucesso() {
        Equipe equipe = new Equipe("EQ-01", tipoEquipe, baseOperacional, veiculo, coordenador, supervisor, processo);

        equipe.alocarMembro(colabMembro, EquipeMembro.FuncaoMembro.RESPONSAVEL);

        assertThat(equipe.getMembros()).hasSize(1);
        assertThat(equipe.getIdUsuario()).isEqualTo(colabMembro.getIdUsuario());
    }
}
