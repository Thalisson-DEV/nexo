package com.nexo.app.modules.estoque.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SolicitacaoMaterialComercialCharacterizationTest {

    @Test
    void prazoDe48HorasEInformativoENaoImpedeCriacaoComercial() {
        TipoEstoque tipo = new TipoEstoque("FISICO");
        Estoque origem = comId(new Estoque("ALMOXARIFADO CENTRAL", tipo));
        Estoque destino = comId(new Estoque("ESTOQUE EQUIPE", new TipoEstoque("CAMPO")));
        destino.vincularEquipe(UUID.randomUUID());

        SolicitacaoMaterial foraDoPrazo = new SolicitacaoMaterial(
                origem, destino, UUID.randomUUID(), LocalDate.now().plusDays(1));
        SolicitacaoMaterial noPrazo = new SolicitacaoMaterial(
                origem, destino, UUID.randomUUID(), LocalDate.now().plusDays(2));

        assertThat(foraDoPrazo.isForaPrazoSaque()).isTrue();
        assertThat(noPrazo.isForaPrazoSaque()).isFalse();
    }

    @Test
    void criacaoComercialComumNascePendenteComItensSemEntrega() {
        SolicitacaoMaterial solicitacao = novaSolicitacao(new BigDecimal("10.500"));

        assertThat(solicitacao.getStatus()).isEqualTo(SolicitacaoMaterial.StatusSolicitacao.PENDENTE);
        assertThat(solicitacao.getDataSolicitacao()).isNotNull();
        assertThat(solicitacao.getItens()).singleElement().satisfies(item -> {
            assertThat(item.getQuantidade()).isEqualByComparingTo("10.500");
            assertThat(item.getQuantidadeEntregue()).isEqualByComparingTo(BigDecimal.ZERO);
        });
    }

    @Test
    void criacaoComercialDiretaEquivaleACriacaoSeguidaDeAprovacao() {
        SolicitacaoMaterial solicitacao = novaSolicitacao(BigDecimal.ONE);
        UUID aprovador = UUID.randomUUID();

        solicitacao.aprovar(aprovador);

        assertThat(solicitacao.getStatus()).isEqualTo(SolicitacaoMaterial.StatusSolicitacao.APROVADO);
        assertThat(solicitacao.getIdAprovador()).isEqualTo(aprovador);
        assertThat(solicitacao.getDataVerificacao()).isNotNull();
    }

    @Test
    void aprovacaoComercialPreservaItensERegistraAprovador() {
        SolicitacaoMaterial solicitacao = novaSolicitacao(new BigDecimal("4.000"));
        UUID aprovador = UUID.randomUUID();

        solicitacao.aprovar(aprovador);

        assertThat(solicitacao.getStatus()).isEqualTo(SolicitacaoMaterial.StatusSolicitacao.APROVADO);
        assertThat(solicitacao.getIdAprovador()).isEqualTo(aprovador);
        assertThat(solicitacao.getItens()).hasSize(1);
    }

    @Test
    void rejeicaoComercialRegistraMotivoEJustificativaNormalizada() {
        SolicitacaoMaterial solicitacao = novaSolicitacao(BigDecimal.ONE);
        MotivoRejeicao motivo = new MotivoRejeicao("SEM SALDO", "Material indisponivel no estoque");

        solicitacao.rejeitar(motivo, "  Sem previsao de reposicao  ");

        assertThat(solicitacao.getStatus()).isEqualTo(SolicitacaoMaterial.StatusSolicitacao.REJEITADO);
        assertThat(solicitacao.getMotivoRejeicao()).isSameAs(motivo);
        assertThat(solicitacao.getJustificativaRejeicao()).isEqualTo("Sem previsao de reposicao");
        assertThat(solicitacao.getDataVerificacao()).isNotNull();
    }

    @Test
    void pendenciasDaSolicitacaoEDoMaterialPreservamOFluxoOperacional() {
        SolicitacaoMaterial solicitacao = novaSolicitacao(BigDecimal.ONE);
        solicitacao.aprovar(UUID.randomUUID());
        ItemSolicitacao item = solicitacao.getItens().getFirst();
        MotivoPendencia motivo = new MotivoPendencia("SEM SALDO", "Material aguardando reposição");
        var status = solicitacao.getStatus();
        var separacao = solicitacao.getStatusSeparacao();

        solicitacao.registrarPendencia(motivo, "  Aguardando reposição  ");
        item.registrarPendencia(motivo, "Aguardando fornecedor");

        assertThat(solicitacao.possuiPendencia()).isTrue();
        assertThat(solicitacao.getJustificativaPendencia()).isEqualTo("Aguardando reposição");
        assertThat(item.getJustificativaPendencia()).isEqualTo("Aguardando fornecedor");
        assertThat(solicitacao.getStatus()).isEqualTo(status);
        assertThat(solicitacao.getStatusSeparacao()).isEqualTo(separacao);

        solicitacao.removerPendencia();
        assertThat(solicitacao.possuiPendencia()).isTrue();
        item.removerPendencia();
        assertThat(solicitacao.possuiPendencia()).isFalse();
    }

    @Test
    void semPendenciaNoMaterialNaoHaPendenciaNaSolicitacao() {
        SolicitacaoMaterial solicitacao = novaSolicitacao(BigDecimal.ONE);
        solicitacao.aprovar(UUID.randomUUID());
        ItemSolicitacao item = solicitacao.getItens().getFirst();
        MotivoPendencia motivo = new MotivoPendencia("SEM SALDO", "Material aguardando reposição");

        solicitacao.registrarPendencia(motivo, "Aguardando reposição geral");
        item.registrarPendencia(motivo, "Item aguardando fornecedor");

        assertThat(solicitacao.possuiPendencia()).isTrue();

        item.removerPendencia();

        assertThat(solicitacao.possuiPendencia()).isFalse();
        assertThat(solicitacao.getMotivoPendencia()).isNull();
        assertThat(solicitacao.getJustificativaPendencia()).isNull();
    }

    @Test
    void entregaComercialParcialMantemSaldoPendente() {
        SolicitacaoMaterial solicitacao = solicitacaoAprovada(new BigDecimal("10.000"));
        ItemSolicitacao item = itemPersistido(solicitacao);

        registrarEntrega(solicitacao, item, new BigDecimal("4.000"));

        assertThat(solicitacao.getStatus()).isEqualTo(SolicitacaoMaterial.StatusSolicitacao.ENTREGUE_PARCIAL);
        assertThat(item.getQuantidadeEntregue()).isEqualByComparingTo("4.000");
        assertThat(item.getQuantidadePendente()).isEqualByComparingTo("6.000");
        assertThat(solicitacao.getDataEntrega()).isNotNull();
    }

    @Test
    void entregaComercialPodeSerConcluidaEmMaisDeUmaOperacao() {
        SolicitacaoMaterial solicitacao = solicitacaoAprovada(new BigDecimal("10.000"));
        ItemSolicitacao item = itemPersistido(solicitacao);

        registrarEntrega(solicitacao, item, new BigDecimal("4.000"));
        registrarEntrega(solicitacao, item, new BigDecimal("6.000"));

        assertThat(solicitacao.getStatus()).isEqualTo(SolicitacaoMaterial.StatusSolicitacao.ENTREGUE);
        assertThat(item.getQuantidadeEntregue()).isEqualByComparingTo("10.000");
        assertThat(item.getQuantidadePendente()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void expiracaoComercialEncerraEntregaParcialSemAlterarAcumulado() {
        SolicitacaoMaterial solicitacao = solicitacaoAprovada(new BigDecimal("10.000"));
        ItemSolicitacao item = itemPersistido(solicitacao);
        registrarEntrega(solicitacao, item, new BigDecimal("4.000"));

        solicitacao.encerrarEntregaParcialExpirada();

        assertThat(solicitacao.getStatus()).isEqualTo(SolicitacaoMaterial.StatusSolicitacao.ENTREGUE_PARCIAL);
        assertThat(solicitacao.estaEncerrada()).isTrue();
        assertThat(solicitacao.isEncerrada()).isTrue();
        assertThat(solicitacao.getEncerradaEm()).isNotNull();
        assertThat(solicitacao.getMotivoEncerramento()).contains("72 horas");
        assertThat(item.getQuantidadeEntregue()).isEqualByComparingTo("4.000");
        assertThat(item.getQuantidadePendente()).isEqualByComparingTo("6.000");
    }

    @Test
    void estornoComercialTotalPreservaEntregaBrutaERegistraEstornoSeparadamente() {
        SolicitacaoMaterial solicitacao = solicitacaoAprovada(new BigDecimal("3.000"));
        ItemSolicitacao item = itemPersistido(solicitacao);
        registrarEntrega(solicitacao, item, new BigDecimal("3.000"));

        solicitacao.estornar();

        assertThat(solicitacao.getStatus()).isEqualTo(SolicitacaoMaterial.StatusSolicitacao.ESTORNADO);
        assertThat(item.getQuantidadeEntregueBruta()).isEqualByComparingTo("3.000");
        assertThat(item.getQuantidadeEntregaEstornada()).isEqualByComparingTo("3.000");
        assertThat(item.getQuantidadeEntregaLiquida()).isEqualByComparingTo("0.000");
        assertThat(item.getQuantidadePendente()).isEqualByComparingTo("3.000");
    }

    @Test
    void estornoComercialParcialReduzQuantidadeLiquidaEReabreSaldo() {
        SolicitacaoMaterial solicitacao = solicitacaoAprovada(new BigDecimal("10.000"));
        ItemSolicitacao item = itemPersistido(solicitacao);
        registrarEntrega(solicitacao, item, new BigDecimal("10.000"));

        solicitacao.registrarEstornoParcial(Map.of(item.getId(), new BigDecimal("2.500")));

        assertThat(solicitacao.getStatus()).isEqualTo(SolicitacaoMaterial.StatusSolicitacao.ENTREGUE_PARCIAL);
        assertThat(item.getQuantidadeEntregue()).isEqualByComparingTo("7.500");
        assertThat(item.getQuantidadeEntregueBruta()).isEqualByComparingTo("10.000");
        assertThat(item.getQuantidadeEntregaEstornada()).isEqualByComparingTo("2.500");
        assertThat(item.getQuantidadePendente()).isEqualByComparingTo("2.500");
    }

    @Test
    void itemSemFatoOperacionalPodeSerRemovidoDuranteEntrega() {
        SolicitacaoMaterial solicitacao = solicitacaoAprovada(BigDecimal.ONE);
        ItemSolicitacao item = itemPersistido(solicitacao);

        solicitacao.removerItem(item.getId());

        assertThat(solicitacao.getItens()).isEmpty();
    }

    @Test
    void itemComEntregaParcialNaoPodeSerRemovidoDuranteEntrega() {
        SolicitacaoMaterial solicitacao = solicitacaoAprovada(new BigDecimal("10.000"));
        ItemSolicitacao item = itemPersistido(solicitacao);
        registrarEntrega(solicitacao, item, new BigDecimal("4.000"));

        assertThatThrownBy(() -> solicitacao.removerItem(item.getId()))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("operações registradas");
        assertThat(solicitacao.getItens()).containsExactly(item);
    }

    @Test
    void itemComEntregaTotalNaoPodeSerRemovidoDuranteEntrega() {
        SolicitacaoMaterial solicitacao = solicitacaoAprovada(new BigDecimal("10.000"));
        ItemSolicitacao item = itemPersistido(solicitacao);
        registrarEntrega(solicitacao, item, new BigDecimal("10.000"));

        assertThatThrownBy(() -> solicitacao.removerItem(item.getId()))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("encerramento da solicitação");
        assertThat(solicitacao.getItens()).containsExactly(item);
    }

    @Test
    void solicitacaoTotalmenteEstornadaPodeReceberNovaEntrega() {
        SolicitacaoMaterial solicitacao = solicitacaoAprovada(new BigDecimal("2.000"));
        ItemSolicitacao item = itemPersistido(solicitacao);
        registrarEntrega(solicitacao, item, new BigDecimal("2.000"));
        solicitacao.estornar();

        registrarEntrega(solicitacao, item, BigDecimal.ONE);

        assertThat(solicitacao.getStatus()).isEqualTo(SolicitacaoMaterial.StatusSolicitacao.ENTREGUE_PARCIAL);
        assertThat(item.getQuantidadeEntregueBruta()).isEqualByComparingTo("3.000");
        assertThat(item.getQuantidadeEntregaEstornada()).isEqualByComparingTo("2.000");
        assertThat(item.getQuantidadeEntregaLiquida()).isEqualByComparingTo("1.000");
        assertThat(item.getQuantidadePendente()).isEqualByComparingTo("1.000");
    }

    private static SolicitacaoMaterial solicitacaoAprovada(BigDecimal quantidade) {
        SolicitacaoMaterial solicitacao = novaSolicitacao(quantidade);
        solicitacao.aprovar(UUID.randomUUID());
        return solicitacao;
    }

    private static SolicitacaoMaterial novaSolicitacao(BigDecimal quantidade) {
        TipoEstoque tipo = new TipoEstoque("FISICO");
        Estoque origem = comId(new Estoque("ALMOXARIFADO CENTRAL", tipo));
        Estoque destino = comId(new Estoque("ESTOQUE EQUIPE", new TipoEstoque("CAMPO")));
        destino.vincularEquipe(UUID.randomUUID());
        Material material = new Material(
                "MAT-001",
                "Material comercial",
                "UN",
                "MATR",
                null,
                null,
                false,
                UUID.randomUUID()
        );

        SolicitacaoMaterial solicitacao = new SolicitacaoMaterial(origem, destino, UUID.randomUUID());
        solicitacao.adicionarItem(material, quantidade);
        return solicitacao;
    }

    private static ItemSolicitacao itemPersistido(SolicitacaoMaterial solicitacao) {
        ItemSolicitacao item = solicitacao.getItens().getFirst();
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
        return item;
    }

    private static void registrarEntrega(
            SolicitacaoMaterial solicitacao,
            ItemSolicitacao item,
            BigDecimal quantidade
    ) {
        solicitacao.registrarEntrega(
                UUID.randomUUID(),
                "assinatura-almox",
                "assinatura-equipe",
                Map.of(item.getId(), new SolicitacaoMaterial.ItemEntregaInfo(quantidade, null, null)),
                List.of()
        );
    }

    private static <T extends BaseEntity> T comId(T entidade) {
        ReflectionTestUtils.setField(entidade, "id", UUID.randomUUID());
        return entidade;
    }
}
