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

class ApontamentoExecucaoTest {

    private static final UUID OBRA = UUID.randomUUID();
    private static final UUID EQUIPE = UUID.randomUUID();
    private static final UUID TURNO = UUID.randomUUID();
    private static final LocalDate DIA = LocalDate.of(2026, 11, 3);

    private final KitConstrutivo catalogo = new KitConstrutivo(UUID.randomUUID(), "LMI-0168", "POSTE", "POSTE", Set.of());
    private final KitAplicado kit = KitAplicado.noPonto(OBRA, UUID.randomUUID(), catalogo, null, BigDecimal.ONE, List.of(
            new KitAplicado.ModeloItem(TipoItemKit.SERVICO, UUID.randomUUID(), "S1", "Instalar poste", OperacaoItemKit.INSTALAR, "UN", new BigDecimal("2")),
            new KitAplicado.ModeloItem(TipoItemKit.MATERIAL, UUID.randomUUID(), "M1", "Poste", OperacaoItemKit.INSTALAR, "UN", BigDecimal.ONE)));
    private final ItemPlanejado servico = kit.getItensAtivos().stream().filter(i -> i.getTipo() == TipoItemKit.SERVICO).findFirst().orElseThrow();
    private final ItemPlanejado material = kit.getItensAtivos().stream().filter(i -> i.getTipo() == TipoItemKit.MATERIAL).findFirst().orElseThrow();
    private final Programacao programacao = programacao();

    private Programacao programacao() {
        kit.getItens().forEach(i -> ReflectionTestUtils.setField(i, "id", UUID.randomUUID()));
        Programacao nova = new Programacao(1687, OBRA, EQUIPE, DIA, DIA.plusDays(2),
                new Programacao.Condicoes("Implantar rede", false, true, false, TurnoProgramacao.DIA_TODO, SituacaoIntervencao.CRIADO, null));
        ReflectionTestUtils.setField(nova, "id", UUID.randomUUID());
        nova.incluirItem(servico, new BigDecimal("150.00"));
        nova.incluirItem(material, null);
        return nova;
    }

    @Test
    void apontarParteDeixaOItemEmAndamentoEComOValorCongelado() {
        ApontamentoExecucao apontamento = programacao.apontar(servico.getId(), BigDecimal.ONE, DIA, TURNO, null);

        assertThat(servico.getStatus()).isEqualTo(StatusItemPlanejado.EM_ANDAMENTO);
        assertThat(servico.getQuantidadeExecutada()).isEqualByComparingTo("1");
        assertThat(apontamento.getValorTotal()).isEqualByComparingTo("150.00");
        assertThat(apontamento.getExcedente()).isEqualByComparingTo("0");
        assertThat(apontamento.getIdEquipe()).isEqualTo(EQUIPE);
        assertThat(apontamento.getIdTurno()).isEqualTo(TURNO);
        assertThat(programacao.situacaoEm(DIA)).isEqualTo(SituacaoProgramacao.EM_ANDAMENTO);
    }

    @Test
    void apontarOTotalConcluiOItem() {
        programacao.apontar(servico.getId(), BigDecimal.ONE, DIA, TURNO, null);
        programacao.apontar(servico.getId(), BigDecimal.ONE, DIA.plusDays(1), TURNO, null);

        assertThat(servico.getStatus()).isEqualTo(StatusItemPlanejado.CONCLUIDO);
        assertThat(servico.concluido()).isTrue();
    }

    @Test
    void materialNaoTemValor() {
        ApontamentoExecucao apontamento = programacao.apontar(material.getId(), BigDecimal.ONE, DIA, TURNO, null);

        assertThat(apontamento.getValorTotal()).isNull();
        assertThat(material.getStatus()).isEqualTo(StatusItemPlanejado.CONCLUIDO);
    }

    @Test
    void passarDoPrevistoExigeJustificativaENaoMudaOItem() {
        assertThatThrownBy(() -> programacao.apontar(servico.getId(), new BigDecimal("3"), DIA, TURNO, "  "))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("justificativa");
    }

    @Test
    void excedenteContaSoAParteQuePassouDoPrevisto() {
        programacao.apontar(servico.getId(), new BigDecimal("1.5"), DIA, TURNO, null);

        ApontamentoExecucao passou = programacao.apontar(servico.getId(), BigDecimal.ONE, DIA, TURNO, "Poste extra pedido em campo");
        ApontamentoExecucao jaPassado = programacao.apontar(servico.getId(), BigDecimal.ONE, DIA, TURNO, "Mais um poste");

        assertThat(passou.getExcedente()).isEqualByComparingTo("0.5");
        assertThat(jaPassado.getExcedente()).isEqualByComparingTo("1");
        assertThat(passou.getJustificativa()).isEqualTo("Poste extra pedido em campo");
    }

    @Test
    void quantidadeDeveSerPositivaEItemDeveEstarNaProgramacao() {
        assertThatThrownBy(() -> programacao.apontar(servico.getId(), BigDecimal.ZERO, DIA, TURNO, null))
                .isInstanceOf(InvarianteVioladaException.class);
        assertThatThrownBy(() -> programacao.apontar(UUID.randomUUID(), BigDecimal.ONE, DIA, TURNO, null))
                .isInstanceOf(InvarianteVioladaException.class);
    }

    @Test
    void programacaoCanceladaNaoRecebeApontamento() {
        programacao.apontar(servico.getId(), BigDecimal.ONE, DIA, TURNO, null);
        programacao.cancelar("Chuva");

        assertThatThrownBy(() -> programacao.apontar(servico.getId(), BigDecimal.ONE, DIA, TURNO, null))
                .isInstanceOf(InvarianteVioladaException.class);
    }

    @Test
    void estornarDevolveAQuantidadeEOStatus() {
        ApontamentoExecucao primeiro = programacao.apontar(servico.getId(), BigDecimal.ONE, DIA, TURNO, null);
        ApontamentoExecucao segundo = programacao.apontar(servico.getId(), BigDecimal.ONE, DIA, TURNO, null);

        programacao.estornar(segundo);
        assertThat(servico.getStatus()).isEqualTo(StatusItemPlanejado.EM_ANDAMENTO);
        assertThat(segundo.isAtivo()).isFalse();

        programacao.estornar(primeiro);
        assertThat(servico.getStatus()).isEqualTo(StatusItemPlanejado.PROGRAMADO);
        assertThat(servico.temExecucao()).isFalse();
    }

    @Test
    void estornarNaCanceladaDevolveOItemParaPendente() {
        ApontamentoExecucao apontamento = programacao.apontar(servico.getId(), BigDecimal.ONE, DIA, TURNO, null);
        programacao.cancelar("Chuva");
        assertThat(servico.getStatus()).isEqualTo(StatusItemPlanejado.EM_ANDAMENTO);

        programacao.estornar(apontamento);

        assertThat(servico.getStatus()).isEqualTo(StatusItemPlanejado.PENDENTE);
    }

    @Test
    void itemMedidoNaoRecebeNemEstornaApontamento() {
        ApontamentoExecucao apontamento = programacao.apontar(servico.getId(), BigDecimal.ONE, DIA, TURNO, null);
        ReflectionTestUtils.setField(servico, "status", StatusItemPlanejado.MEDIDO);

        assertThatThrownBy(() -> programacao.apontar(servico.getId(), BigDecimal.ONE, DIA, TURNO, null))
                .isInstanceOf(InvarianteVioladaException.class);
        assertThatThrownBy(() -> programacao.estornar(apontamento))
                .isInstanceOf(InvarianteVioladaException.class);
    }
}
