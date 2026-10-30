package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KitConstrutivoTest {

    private final UUID contrato = UUID.randomUUID();

    @Test
    void normalizaCodigoEGrupo() {
        KitConstrutivo kit = new KitConstrutivo(contrato, " lmi-0688 ", "Estrutura BT", " estrutura ", Set.of());

        assertThat(kit.getCodigo()).isEqualTo("LMI-0688");
        assertThat(kit.getGrupo()).isEqualTo("ESTRUTURA");
    }

    @Test
    void exigeContratoCodigoEDescricao() {
        assertThatThrownBy(() -> new KitConstrutivo(null, "K1", "Desc", null, null))
                .isInstanceOf(InvarianteVioladaException.class);
        assertThatThrownBy(() -> new KitConstrutivo(contrato, " ", "Desc", null, null))
                .isInstanceOf(InvarianteVioladaException.class);
        assertThatThrownBy(() -> new KitConstrutivo(contrato, "K1", "", null, null))
                .isInstanceOf(InvarianteVioladaException.class);
    }

    @Test
    void itemDeServicoApontaParaAcaoEItemDeMaterialParaMaterial() {
        KitConstrutivo kit = new KitConstrutivo(contrato, "K1", "Kit", null, null);
        UUID acao = UUID.randomUUID();
        UUID material = UUID.randomUUID();

        ItemKit servico = kit.adicionarItem(TipoItemKit.SERVICO, acao, null, "un", BigDecimal.ONE);
        ItemKit mat = kit.adicionarItem(TipoItemKit.MATERIAL, material, OperacaoItemKit.RETIRAR, null, BigDecimal.TEN);

        assertThat(servico.getIdAcao()).isEqualTo(acao);
        assertThat(servico.getIdMaterial()).isNull();
        assertThat(servico.getOperacao()).isEqualTo(OperacaoItemKit.INSTALAR);
        assertThat(servico.getUnidade()).isEqualTo("UN");
        assertThat(mat.getIdMaterial()).isEqualTo(material);
        assertThat(mat.getIdAcao()).isNull();
        assertThat(kit.getItensAtivos()).hasSize(2);
    }

    @Test
    void rejeitaQuantidadeNaoPositivaEItemDuplicado() {
        KitConstrutivo kit = new KitConstrutivo(contrato, "K1", "Kit", null, null);
        UUID acao = UUID.randomUUID();

        assertThatThrownBy(() -> kit.adicionarItem(TipoItemKit.SERVICO, acao, null, null, BigDecimal.ZERO))
                .isInstanceOf(InvarianteVioladaException.class);

        kit.adicionarItem(TipoItemKit.SERVICO, acao, OperacaoItemKit.INSTALAR, null, BigDecimal.ONE);
        assertThatThrownBy(() -> kit.adicionarItem(TipoItemKit.SERVICO, acao, OperacaoItemKit.INSTALAR, null, BigDecimal.ONE))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("já possui");

        // Mesma ação para retirar é outro item.
        kit.adicionarItem(TipoItemKit.SERVICO, acao, OperacaoItemKit.RETIRAR, null, BigDecimal.ONE);
        assertThat(kit.getItensAtivos()).hasSize(2);
    }

    @Test
    void adicionarOuAtualizarAtualizaItemExistente() {
        KitConstrutivo kit = new KitConstrutivo(contrato, "K1", "Kit", null, null);
        UUID material = UUID.randomUUID();

        assertThat(kit.adicionarOuAtualizarItem(TipoItemKit.MATERIAL, material, null, "M", BigDecimal.ONE)).isTrue();
        assertThat(kit.adicionarOuAtualizarItem(TipoItemKit.MATERIAL, material, null, "M", new BigDecimal("3"))).isFalse();

        assertThat(kit.getItensAtivos()).singleElement()
                .extracting(ItemKit::getQuantidade).isEqualTo(new BigDecimal("3"));
    }

    @Test
    void kitVazioNaoPodeSerAplicado() {
        KitConstrutivo kit = new KitConstrutivo(contrato, "K1", "Kit", null, null);

        assertThatThrownBy(() -> kit.validarAplicacao(null))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("vazio");
    }

    @Test
    void kitDesativadoNaoPodeSerAplicadoNemAlterado() {
        KitConstrutivo kit = new KitConstrutivo(contrato, "K1", "Kit", null, null);
        kit.adicionarItem(TipoItemKit.SERVICO, UUID.randomUUID(), null, null, BigDecimal.ONE);
        kit.desativar();

        assertThatThrownBy(() -> kit.validarAplicacao(null)).hasMessageContaining("desativado");
        assertThatThrownBy(() -> kit.atualizarDados("Outro", null, null)).isInstanceOf(InvarianteVioladaException.class);
        // Os itens continuam visíveis para as obras onde o kit já foi aplicado.
        assertThat(kit.getItensAtivos()).hasSize(1);
    }

    @Test
    void classeDeTensaoFiltraAplicacao() {
        KitConstrutivo semTensao = new KitConstrutivo(contrato, "K1", "Kit", null, Set.of());
        KitConstrutivo soMt = new KitConstrutivo(contrato, "K2", "Kit", null, Set.of(ClasseTensao.MT_15));
        soMt.adicionarItem(TipoItemKit.SERVICO, UUID.randomUUID(), null, null, BigDecimal.ONE);

        assertThat(semTensao.aplicavelEm(ClasseTensao.BT)).isTrue();
        assertThat(soMt.aplicavelEm(ClasseTensao.MT_15)).isTrue();
        assertThat(soMt.aplicavelEm(ClasseTensao.BT)).isFalse();
        assertThat(soMt.aplicavelEm(null)).isTrue();
        assertThatThrownBy(() -> soMt.validarAplicacao(ClasseTensao.BT)).hasMessageContaining("BT");
    }

    @Test
    void atualizarDadosComAsMesmasTensoesNaoAsApaga() {
        KitConstrutivo kit = new KitConstrutivo(contrato, "K1", "Kit", null, Set.of(ClasseTensao.MT_15, ClasseTensao.BT));

        kit.atualizarDados("Kit", null, kit.getClassesTensao());

        assertThat(kit.getClassesTensao()).containsExactlyInAnyOrder(ClasseTensao.MT_15, ClasseTensao.BT);
    }

    @Test
    void interpretaClasseDeTensaoDePlanilha() {
        assertThat(ClasseTensao.interpretar("15 kV")).contains(ClasseTensao.MT_15);
        assertThat(ClasseTensao.interpretar("13,8kV")).contains(ClasseTensao.MT_15);
        assertThat(ClasseTensao.interpretar("bt")).contains(ClasseTensao.BT);
        assertThat(ClasseTensao.interpretar("MT_36")).contains(ClasseTensao.MT_36);
        assertThat(ClasseTensao.interpretar("xyz")).isEmpty();
    }
}
