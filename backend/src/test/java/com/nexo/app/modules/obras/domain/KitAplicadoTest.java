package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KitAplicadoTest {

    private static final UUID OBRA = UUID.randomUUID();

    private final KitConstrutivo kit = new KitConstrutivo(UUID.randomUUID(), "LMI-0168", "CABO BT", "VAO BT", Set.of());

    private final List<KitAplicado.ModeloItem> cabo = List.of(
            modelo(TipoItemKit.MATERIAL, "CABO", "M", "1.04"),
            modelo(TipoItemKit.SERVICO, "LANCAMENTO", "M", "1.04"),
            modelo(TipoItemKit.MATERIAL, "ALCA", "CDA", "1"));

    private static KitAplicado.ModeloItem modelo(TipoItemKit tipo, String descricao, String unidade, String quantidade) {
        return new KitAplicado.ModeloItem(tipo, UUID.randomUUID(), descricao, descricao, OperacaoItemKit.INSTALAR,
                unidade, new BigDecimal(quantidade));
    }

    private static BigDecimal prevista(KitAplicado aplicado, String descricao) {
        return aplicado.getItensAtivos().stream()
                .filter(i -> i.getDescricao().equals(descricao))
                .findFirst().orElseThrow()
                .getQuantidadePrevista();
    }

    @Test
    void metragemDoVaoEscalaSoOsItensEmMetro() {
        KitAplicado aplicado = KitAplicado.noVao(OBRA, UUID.randomUUID(), kit, null, new BigDecimal("35"), cabo);

        assertThat(prevista(aplicado, "CABO")).isEqualByComparingTo("36.4");
        assertThat(prevista(aplicado, "LANCAMENTO")).isEqualByComparingTo("36.4");
        assertThat(prevista(aplicado, "ALCA")).isEqualByComparingTo("1");
    }

    @Test
    void quantidadeNoPontoEscalaTodosOsItens() {
        KitAplicado aplicado = KitAplicado.noPonto(OBRA, UUID.randomUUID(), kit, null, new BigDecimal("2"), cabo);

        assertThat(prevista(aplicado, "CABO")).isEqualByComparingTo("2.08");
        assertThat(prevista(aplicado, "ALCA")).isEqualByComparingTo("2");
    }

    @Test
    void alterarMetragemRecalculaOPrevisto() {
        KitAplicado aplicado = KitAplicado.noVao(OBRA, UUID.randomUUID(), kit, null, new BigDecimal("35"), cabo);

        aplicado.alterarMultiplicador(new BigDecimal("10"));

        assertThat(prevista(aplicado, "CABO")).isEqualByComparingTo("10.4");
        assertThat(prevista(aplicado, "ALCA")).isEqualByComparingTo("1");
    }

    @Test
    void retirarValeParaTodosOsItens() {
        KitAplicado aplicado = KitAplicado.noPonto(OBRA, UUID.randomUUID(), kit, OperacaoItemKit.RETIRAR, null, cabo);

        assertThat(aplicado.getItensAtivos()).allMatch(i -> i.getOperacao() == OperacaoItemKit.RETIRAR);
    }

    @Test
    void itemProgramadoBloqueiaRemoverEAlterarOKit() {
        KitAplicado aplicado = KitAplicado.noPonto(OBRA, UUID.randomUUID(), kit, null, null, cabo);
        ReflectionTestUtils.setField(aplicado.getItensAtivos().get(0), "status", StatusItemPlanejado.PROGRAMADO);

        assertThatThrownBy(aplicado::remover)
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("CABO");
        assertThatThrownBy(() -> aplicado.alterarMultiplicador(BigDecimal.TEN))
                .isInstanceOf(InvarianteVioladaException.class);
        assertThat(aplicado.isAtivo()).isTrue();
    }

    @Test
    void executadoAntesDoSistemaContaComoConcluidoEPodeSerDesfeito() {
        KitAplicado aplicado = KitAplicado.noPonto(OBRA, UUID.randomUUID(), kit, null, null, cabo);

        aplicado.marcarExecutadoAntes(true);

        assertThat(aplicado.getItensAtivos()).allSatisfy(i -> {
            assertThat(i.getStatus()).isEqualTo(StatusItemPlanejado.CONCLUIDO);
            assertThat(i.getQuantidadeExecutada()).isEqualByComparingTo(i.getQuantidadePrevista());
        });

        aplicado.marcarExecutadoAntes(false);

        assertThat(aplicado.getItensAtivos()).allSatisfy(i -> {
            assertThat(i.getStatus()).isEqualTo(StatusItemPlanejado.PENDENTE);
            assertThat(i.getQuantidadeExecutada()).isEqualByComparingTo("0");
        });
    }

    @Test
    void vaoNaoLigaPontoAEleMesmo() {
        UUID ponto = UUID.randomUUID();

        assertThatThrownBy(() -> new VaoPlanejamento(OBRA, ponto, ponto, null, null))
                .isInstanceOf(InvarianteVioladaException.class);
    }

    @Test
    void pontoForaDaFolhaERecusado() {
        assertThatThrownBy(() -> new PontoPlanejamento(OBRA, "4", 1, new BigDecimal("1.2"), BigDecimal.ZERO, null))
                .isInstanceOf(InvarianteVioladaException.class);
    }

    @Test
    void rotacaoAceitaSoAngulosRetos() {
        PlanejamentoObra planejamento = new PlanejamentoObra(OBRA);

        planejamento.girarPagina(2, 90);
        assertThat(planejamento.getRotacoes()).containsEntry("2", 90);

        planejamento.girarPagina(2, 0);
        assertThat(planejamento.getRotacoes()).doesNotContainKey("2");

        assertThatThrownBy(() -> planejamento.girarPagina(2, 45)).isInstanceOf(InvarianteVioladaException.class);
    }
}
