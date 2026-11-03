package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProgramacaoTest {

    private static final UUID OBRA = UUID.randomUUID();
    private static final UUID EQUIPE = UUID.randomUUID();
    private static final LocalDate INICIO = LocalDate.of(2026, 11, 3);
    private static final LocalDate FIM = LocalDate.of(2026, 11, 5);

    private final KitConstrutivo catalogo = new KitConstrutivo(UUID.randomUUID(), "LMI-0168", "POSTE", "POSTE", Set.of());

    private KitAplicado kit() {
        KitAplicado kit = KitAplicado.noPonto(OBRA, UUID.randomUUID(), catalogo, null, BigDecimal.ONE, List.of(
                new KitAplicado.ModeloItem(TipoItemKit.SERVICO, UUID.randomUUID(), "S1", "Instalar poste", OperacaoItemKit.INSTALAR, "UN", new BigDecimal("2")),
                new KitAplicado.ModeloItem(TipoItemKit.MATERIAL, UUID.randomUUID(), "M1", "Poste", OperacaoItemKit.INSTALAR, "UN", BigDecimal.ONE)));
        kit.getItens().forEach(i -> ReflectionTestUtils.setField(i, "id", UUID.randomUUID()));
        return kit;
    }

    private static Programacao.Condicoes condicoes() {
        return new Programacao.Condicoes("Implantar rede", false, true, false, TurnoProgramacao.DIA_TODO, SituacaoIntervencao.CRIADO, null);
    }

    private static ItemPlanejado servico(KitAplicado kit) {
        return kit.getItensAtivos().stream().filter(i -> i.getTipo() == TipoItemKit.SERVICO).findFirst().orElseThrow();
    }

    private static ItemPlanejado material(KitAplicado kit) {
        return kit.getItensAtivos().stream().filter(i -> i.getTipo() == TipoItemKit.MATERIAL).findFirst().orElseThrow();
    }

    private Programacao comKit(KitAplicado kit) {
        Programacao programacao = new Programacao(1687, OBRA, EQUIPE, INICIO, FIM, condicoes());
        programacao.incluirItem(servico(kit), new BigDecimal("150.00"));
        programacao.incluirItem(material(kit), null);
        return programacao;
    }

    @Test
    void congelaOValorDoServicoEMarcaOsItens() {
        KitAplicado kit = kit();
        Programacao programacao = comKit(kit);

        assertThat(programacao.valorTotal()).isEqualByComparingTo("300.00");
        assertThat(servico(kit).getStatus()).isEqualTo(StatusItemPlanejado.PROGRAMADO);
        assertThat(material(kit).getStatus()).isEqualTo(StatusItemPlanejado.PROGRAMADO);
    }

    @Test
    void valorCongeladoNaoMudaAoReprogramar() {
        Programacao programacao = comKit(kit());
        UUID outraEquipe = UUID.randomUUID();

        EventoProgramacao evento = programacao.reprogramar(outraEquipe, INICIO.plusDays(7), FIM.plusDays(7), "turma errada");

        assertThat(programacao.valorTotal()).isEqualByComparingTo("300.00");
        assertThat(programacao.getIdEquipe()).isEqualTo(outraEquipe);
        assertThat(evento.getTipo()).isEqualTo(TipoEventoProgramacao.REPROGRAMADA);
        assertThat(evento.getIdEquipeOrigem()).isEqualTo(EQUIPE);
        assertThat(evento.getIdEquipeDestino()).isEqualTo(outraEquipe);
        assertThat(evento.getInicioOriginal()).isEqualTo(INICIO);
        assertThat(evento.getInicioNovo()).isEqualTo(INICIO.plusDays(7));
        assertThat(evento.getObservacao()).isEqualTo("turma errada");
    }

    @Test
    void reprogramarECancelarExigemObservacao() {
        Programacao programacao = comKit(kit());

        assertThatThrownBy(() -> programacao.reprogramar(UUID.randomUUID(), INICIO, FIM, " "))
                .isInstanceOf(InvarianteVioladaException.class);
        assertThatThrownBy(() -> programacao.cancelar(null))
                .isInstanceOf(InvarianteVioladaException.class);
    }

    @Test
    void reprogramarSemMudarNadaERecusado() {
        Programacao programacao = comKit(kit());

        assertThatThrownBy(() -> programacao.reprogramar(EQUIPE, INICIO, FIM, "sem mudança"))
                .isInstanceOf(InvarianteVioladaException.class);
    }

    @Test
    void desligamentoEObrigatorio() {
        Programacao.Condicoes semDesligamento = new Programacao.Condicoes("Rede", false, null, false,
                TurnoProgramacao.MANHA, SituacaoIntervencao.PENDENTE, null);

        assertThatThrownBy(() -> new Programacao(1, OBRA, EQUIPE, INICIO, FIM, semDesligamento))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("desligamento");
    }

    @Test
    void periodoInvertidoERecusado() {
        assertThatThrownBy(() -> new Programacao(1, OBRA, EQUIPE, FIM, INICIO, condicoes()))
                .isInstanceOf(InvarianteVioladaException.class);
    }

    @Test
    void cancelarDevolveOsItensEGuardaOValorDoHistorico() {
        KitAplicado kit = kit();
        Programacao programacao = comKit(kit);

        EventoProgramacao evento = programacao.cancelar("obra suspensa");

        assertThat(evento.getTipo()).isEqualTo(TipoEventoProgramacao.CANCELADA);
        assertThat(servico(kit).getStatus()).isEqualTo(StatusItemPlanejado.PENDENTE);
        assertThat(material(kit).getStatus()).isEqualTo(StatusItemPlanejado.PENDENTE);
        assertThat(programacao.getItensAtivos()).isEmpty();
        assertThat(programacao.valorTotal()).isEqualByComparingTo("300.00");
        assertThat(programacao.situacaoEm(INICIO)).isEqualTo(SituacaoProgramacao.CANCELADA);
        assertThatThrownBy(() -> programacao.reprogramar(UUID.randomUUID(), INICIO, FIM, "voltar"))
                .isInstanceOf(InvarianteVioladaException.class);
    }

    @Test
    void cancelarMantemOItemComExecucao() {
        KitAplicado kit = kit();
        Programacao programacao = comKit(kit);
        ReflectionTestUtils.setField(servico(kit), "quantidadeExecutada", BigDecimal.ONE);

        programacao.cancelar("parou no meio");

        assertThat(servico(kit).getStatus()).isEqualTo(StatusItemPlanejado.PROGRAMADO);
        assertThat(material(kit).getStatus()).isEqualTo(StatusItemPlanejado.PENDENTE);
        assertThat(programacao.getItensAtivos()).hasSize(1);
    }

    @Test
    void excluirComExecucaoERecusado() {
        KitAplicado kit = kit();
        Programacao programacao = comKit(kit);
        ReflectionTestUtils.setField(servico(kit), "quantidadeExecutada", BigDecimal.ONE);

        assertThatThrownBy(() -> programacao.excluir(null))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("Cancele");
    }

    @Test
    void excluirDesativaEVoltaOsItensParaPendente() {
        KitAplicado kit = kit();
        Programacao programacao = comKit(kit);

        EventoProgramacao evento = programacao.excluir(null);

        assertThat(evento.getTipo()).isEqualTo(TipoEventoProgramacao.EXCLUIDA);
        assertThat(programacao.isAtivo()).isFalse();
        assertThat(servico(kit).programavel()).isTrue();
    }

    @Test
    void retirarItemComExecucaoERecusado() {
        KitAplicado kit = kit();
        Programacao programacao = comKit(kit);
        ReflectionTestUtils.setField(servico(kit), "quantidadeExecutada", BigDecimal.ONE);

        assertThatThrownBy(() -> programacao.retirarItem(servico(kit).getId()))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("execução");
    }

    @Test
    void retirarOUltimoServicoLevaOMaterialEExcluiAProgramacao() {
        KitAplicado kit = kit();
        Programacao programacao = comKit(kit);

        EventoProgramacao evento = programacao.retirarItem(servico(kit).getId());

        assertThat(material(kit).getStatus()).isEqualTo(StatusItemPlanejado.PENDENTE);
        assertThat(evento.getTipo()).isEqualTo(TipoEventoProgramacao.EXCLUIDA);
        assertThat(programacao.isAtivo()).isFalse();
    }

    @Test
    void retirarUmServicoMantemOsDemaisEORegistraEdicao() {
        KitAplicado kitA = kit();
        KitAplicado kitB = kit();
        Programacao programacao = comKit(kitA);
        programacao.incluirItem(servico(kitB), new BigDecimal("10.00"));

        EventoProgramacao evento = programacao.retirarItem(servico(kitB).getId());

        assertThat(evento.getTipo()).isEqualTo(TipoEventoProgramacao.EDITADA);
        assertThat(programacao.getItensAtivos()).hasSize(2);
        assertThat(programacao.valorTotal()).isEqualByComparingTo("300.00");
    }

    @Test
    void editarSemMudarNaoGeraEvento() {
        Programacao programacao = comKit(kit());

        assertThat(programacao.editar(condicoes())).isEmpty();
    }

    @Test
    void editarRegistraOQueMudou() {
        Programacao programacao = comKit(kit());

        EventoProgramacao evento = programacao.editar(new Programacao.Condicoes("Implantar rede", true, true, false,
                TurnoProgramacao.MANHA, SituacaoIntervencao.CRIADO, null)).orElseThrow();

        assertThat(evento.getObservacao()).contains("Linha viva: Não → Sim").contains("Turno: Dia todo → Manhã");
    }

    @Test
    void itemJaProgramadoNaoEntraEmOutraProgramacao() {
        KitAplicado kit = kit();
        comKit(kit);
        Programacao outra = new Programacao(2, OBRA, EQUIPE, INICIO, FIM, condicoes());

        assertThatThrownBy(() -> outra.incluirItem(servico(kit), BigDecimal.TEN))
                .isInstanceOf(InvarianteVioladaException.class);
    }

    @Test
    void kitProgramadoNaoPodeSerRemovidoDoPlanejamento() {
        KitAplicado kit = kit();
        comKit(kit);

        assertThatThrownBy(kit::remover).isInstanceOf(InvarianteVioladaException.class);
    }

    @Test
    void situacaoAcompanhaADataEAExecucao() {
        KitAplicado kit = kit();
        Programacao programacao = comKit(kit);

        assertThat(programacao.situacaoEm(INICIO)).isEqualTo(SituacaoProgramacao.PROGRAMADA);
        assertThat(programacao.situacaoEm(FIM.plusDays(1))).isEqualTo(SituacaoProgramacao.ATRASADA);

        ReflectionTestUtils.setField(servico(kit), "quantidadeExecutada", BigDecimal.ONE);
        assertThat(programacao.situacaoEm(INICIO)).isEqualTo(SituacaoProgramacao.EM_ANDAMENTO);

        ReflectionTestUtils.setField(servico(kit), "status", StatusItemPlanejado.CONCLUIDO);
        ReflectionTestUtils.setField(material(kit), "status", StatusItemPlanejado.CONCLUIDO);
        assertThat(programacao.situacaoEm(FIM.plusDays(1))).isEqualTo(SituacaoProgramacao.CONCLUIDA);
    }
}
