package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MedicaoTest {

    private static final UUID OBRA = UUID.randomUUID();
    private static final LocalDate DIA = LocalDate.of(2026, 11, 3);
    private static final YearMonth NOVEMBRO = YearMonth.of(2026, 11);

    private final KitConstrutivo catalogo = new KitConstrutivo(UUID.randomUUID(), "LMI-0168", "POSTE", "POSTE", Set.of());
    private final KitAplicado kit = KitAplicado.noPonto(OBRA, UUID.randomUUID(), catalogo, null, BigDecimal.ONE, List.of(
            new KitAplicado.ModeloItem(TipoItemKit.SERVICO, UUID.randomUUID(), "S1", "Instalar poste", OperacaoItemKit.INSTALAR, "UN", new BigDecimal("2")),
            new KitAplicado.ModeloItem(TipoItemKit.SERVICO, UUID.randomUUID(), "S2", "Instalar cruzeta", OperacaoItemKit.INSTALAR, "UN", BigDecimal.ONE),
            new KitAplicado.ModeloItem(TipoItemKit.MATERIAL, UUID.randomUUID(), "M1", "Poste", OperacaoItemKit.INSTALAR, "UN", BigDecimal.ONE)));
    private final ItemPlanejado poste = item("S1");
    private final ItemPlanejado cruzeta = item("S2");
    private final ItemPlanejado material = item("M1");
    private final Programacao programacao = programacao();

    private ItemPlanejado item(String codigo) {
        return kit.getItensAtivos().stream().filter(i -> i.getCodigo().equals(codigo)).findFirst().orElseThrow();
    }

    private Programacao programacao() {
        kit.getItens().forEach(i -> ReflectionTestUtils.setField(i, "id", UUID.randomUUID()));
        Programacao nova = new Programacao(10, OBRA, UUID.randomUUID(), DIA, DIA.plusDays(5),
                new Programacao.Condicoes("Implantar rede", false, true, false, TurnoProgramacao.DIA_TODO, SituacaoIntervencao.CRIADO, null));
        ReflectionTestUtils.setField(nova, "id", UUID.randomUUID());
        nova.incluirItem(poste, new BigDecimal("150.00"));
        nova.incluirItem(cruzeta, new BigDecimal("80.00"));
        nova.incluirItem(material, null);
        return nova;
    }

    private Medicao.ItemAMedir concluir(ItemPlanejado item, LocalDate dia) {
        ApontamentoExecucao apontamento = programacao.apontar(item.getId(), item.getQuantidadePrevista(), dia, null, null);
        return new Medicao.ItemAMedir(item, apontamento.getQuantidade(), apontamento.getValorTotal(), dia);
    }

    @Test
    void gerarMedeOsServicosConcluidosComOValorApontado() {
        Medicao medicao = Medicao.gerar(() -> 1, OBRA, TipoMedicao.PARCIAL, NOVEMBRO, DIA,
                List.of(concluir(poste, DIA), concluir(cruzeta, DIA.plusDays(1))), "Primeira parcial");

        assertThat(medicao.getValorTotal()).isEqualByComparingTo("380.00");
        assertThat(medicao.getItens()).hasSize(2);
        assertThat(medicao.getCompetencia()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(poste.getStatus()).isEqualTo(StatusItemPlanejado.MEDIDO);
        assertThat(poste.medivel()).isFalse();
        assertThat(medicao.getItens().getFirst().getValorUnitario()).isEqualByComparingTo("150.00");
    }

    @Test
    void itemMedidoNaoEntraEmOutraMedicaoNemRecebeApontamento() {
        Medicao.ItemAMedir item = concluir(poste, DIA);
        Medicao.gerar(() -> 1, OBRA, TipoMedicao.PARCIAL, NOVEMBRO, DIA, List.of(item), null);

        assertThatThrownBy(() -> Medicao.gerar(() -> 2, OBRA, TipoMedicao.PARCIAL, NOVEMBRO, DIA, List.of(item), null))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("pendente de medição");
        assertThatThrownBy(() -> programacao.apontar(poste.getId(), BigDecimal.ONE, DIA, null, "mais"))
                .isInstanceOf(InvarianteVioladaException.class);
    }

    @Test
    void servicoEmAndamentoEMaterialNaoSaoMediveis() {
        programacao.apontar(poste.getId(), BigDecimal.ONE, DIA, null, null);
        programacao.apontar(material.getId(), BigDecimal.ONE, DIA, null, null);

        assertThat(poste.medivel()).isFalse();
        assertThat(material.concluido()).isTrue();
        assertThat(material.medivel()).isFalse();
    }

    @Test
    void kitExecutadoAntesDoSistemaNaoEMedido() {
        KitAplicado antigo = KitAplicado.noPonto(OBRA, UUID.randomUUID(), catalogo, null, BigDecimal.ONE, List.of(
                new KitAplicado.ModeloItem(TipoItemKit.SERVICO, UUID.randomUUID(), "S1", "Instalar poste", OperacaoItemKit.INSTALAR, "UN", BigDecimal.ONE)));
        antigo.marcarExecutadoAntes(true);

        assertThat(antigo.getItensAtivos().getFirst().concluido()).isTrue();
        assertThat(antigo.getItensAtivos().getFirst().medivel()).isFalse();
    }

    @Test
    void estornarDevolveOsItensAConcluidoEMantemAMedicao() {
        Medicao medicao = Medicao.gerar(() -> 1, OBRA, TipoMedicao.PARCIAL, NOVEMBRO, DIA, List.of(concluir(poste, DIA)), null);

        medicao.estornar("Valor contestado", "USRADMIN", OffsetDateTime.now());

        assertThat(medicao.estornada()).isTrue();
        assertThat(medicao.getJustificativaEstorno()).isEqualTo("Valor contestado");
        assertThat(medicao.getItens()).allMatch(i -> !i.isAtivo());
        assertThat(poste.getStatus()).isEqualTo(StatusItemPlanejado.CONCLUIDO);
        assertThat(poste.medivel()).isTrue();
    }

    @Test
    void estornoExigeJustificativaEAconteceUmaVez() {
        Medicao medicao = Medicao.gerar(() -> 1, OBRA, TipoMedicao.PARCIAL, NOVEMBRO, DIA, List.of(concluir(poste, DIA)), null);

        assertThatThrownBy(() -> medicao.estornar(" ", "USRADMIN", OffsetDateTime.now()))
                .hasMessageContaining("justificativa");
        medicao.estornar("Erro", "USRADMIN", OffsetDateTime.now());
        assertThatThrownBy(() -> medicao.estornar("De novo", "USRADMIN", OffsetDateTime.now()))
                .hasMessageContaining("já foi estornada");
    }

    @Test
    void cicloRecusaServicoConcluidoDepoisDoFimDaCompetencia() {
        Medicao.ItemAMedir dezembro = concluir(poste, LocalDate.of(2026, 12, 2));

        assertThatThrownBy(() -> Medicao.gerar(() -> 1, OBRA, TipoMedicao.CICLO, NOVEMBRO, LocalDate.of(2026, 12, 3), List.of(dezembro), null))
                .hasMessageContaining("depois do fim do ciclo");
        assertThat(poste.getStatus()).isEqualTo(StatusItemPlanejado.CONCLUIDO);
    }

    @Test
    void competenciaNoFuturoOuSemItensERecusada() {
        assertThatThrownBy(() -> Medicao.gerar(() -> 1, OBRA, TipoMedicao.CICLO, NOVEMBRO.plusMonths(1), DIA, List.of(concluir(poste, DIA)), null))
                .hasMessageContaining("futuro");
        assertThatThrownBy(() -> Medicao.gerar(() -> 1, OBRA, TipoMedicao.PARCIAL, NOVEMBRO, DIA, List.of(), null))
                .hasMessageContaining("Não há serviço");
    }

    @Test
    void servicoDeOutraObraERecusado() {
        Medicao.ItemAMedir item = concluir(poste, DIA);

        assertThatThrownBy(() -> Medicao.gerar(() -> 1, UUID.randomUUID(), TipoMedicao.PARCIAL, NOVEMBRO, DIA, List.of(item), null))
                .hasMessageContaining("outra obra");
    }

    @Test
    void levantamentoSoConcluiSemPontoOuVaoSemKit() {
        PlanejamentoObra planejamento = new PlanejamentoObra(OBRA);

        assertThatThrownBy(() -> planejamento.concluirLevantamento(0, List.of(), 0, "USRADMIN", OffsetDateTime.now()))
                .hasMessageContaining("não tem pontos");
        assertThatThrownBy(() -> planejamento.concluirLevantamento(5, List.of("P3", "P4"), 1, "USRADMIN", OffsetDateTime.now()))
                .hasMessageContaining("2 pontos sem kit (P3, P4) e 1 vão sem kit");

        planejamento.concluirLevantamento(5, List.of(), 0, "USRADMIN", OffsetDateTime.now());
        assertThat(planejamento.levantamentoConcluido()).isTrue();
        assertThat(planejamento.getLevantamentoConcluidoPor()).isEqualTo("USRADMIN");

        planejamento.reabrirLevantamento();
        assertThat(planejamento.levantamentoConcluido()).isFalse();
    }
}
