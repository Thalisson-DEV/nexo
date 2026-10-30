package com.nexo.app.modules.estoque.domain;

import com.nexo.app.modules.obras.ObraApi.ObraPublica;
import com.nexo.app.modules.obras.ObraApi.TipoObraPublico;
import com.nexo.app.shared.domain.BaseEntity;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ItemSolicitacaoTecnicaTest {

    @Test
    void adicaoMantemBrutoEstornoLiquidoPendenteEProjecaoLegada() {
        ItemSolicitacao item = itemTecnico(
                TipoSolicitacaoTecnica.ADICAO_COMUM, new BigDecimal("10.000"));

        item.registrarEntregaTecnica(new BigDecimal("4.500"));
        item.registrarEstornoEntregaTecnica(new BigDecimal("1.250"));

        assertThat(item.getQuantidadeEntregueBruta()).isEqualByComparingTo("4.500");
        assertThat(item.getQuantidadeEntregaEstornada()).isEqualByComparingTo("1.250");
        assertThat(item.getQuantidadeEntregaLiquida()).isEqualByComparingTo("3.250");
        assertThat(item.getQuantidadePendente()).isEqualByComparingTo("6.750");
        assertThat(item.getQuantidadeEntregue()).isEqualByComparingTo("3.250");
    }

    @Test
    void devolucaoMantemAcumuladoresIndependentesDaEntrega() {
        ItemSolicitacao item = itemTecnico(
                TipoSolicitacaoTecnica.DEVOLUCAO_CONCRETO, new BigDecimal("5.000"));

        item.registrarDevolucaoTecnica(new BigDecimal("3.000"));
        item.registrarEstornoDevolucaoTecnica(new BigDecimal("1.000"));

        assertThat(item.getQuantidadeDevolvidaBruta()).isEqualByComparingTo("3.000");
        assertThat(item.getQuantidadeDevolucaoEstornada()).isEqualByComparingTo("1.000");
        assertThat(item.getQuantidadeDevolucaoLiquida()).isEqualByComparingTo("2.000");
        assertThat(item.getQuantidadePendente()).isEqualByComparingTo("3.000");
        assertThat(item.getQuantidadeEntregue()).isZero();
    }

    @Test
    void trocaDeMaterialNoPlanejamentoRemovePendenciaDoMaterialAnterior() {
        ItemSolicitacao item = itemTecnico(TipoSolicitacaoTecnica.ADICAO_COMUM, BigDecimal.ONE);
        item.registrarPendencia(new MotivoPendencia("SEM SALDO", "Material indisponível"), "Aguardando reposição");
        Material outro = comId(new Material("MAT-NOVO", "Novo material", "UN", "MATR",
                null, null, false, UUID.randomUUID()));

        item.replanejar(outro, BigDecimal.ONE);

        assertThat(item.possuiPendencia()).isFalse();
    }

    private static ItemSolicitacao itemTecnico(
            TipoSolicitacaoTecnica tipo,
            BigDecimal quantidade
    ) {
        Material material = comId(new Material(
                "MAT-" + UUID.randomUUID(), "Material tecnico", "UN", "MATR",
                null, null, false, UUID.randomUUID()));
        TipoEstoque tipoFisico = comId(new TipoEstoque("FISICO"));
        TipoEstoque tipoObra = comId(new TipoEstoque("OBRA"));
        Estoque almoxarifado = comId(new Estoque("ALMOXARIFADO", tipoFisico));
        Estoque estoqueObra = comId(new Estoque("OBRA 001", tipoObra));
        ObraPublica obra = new ObraPublica(
                UUID.randomUUID(), "001", "Obra teste", TipoObraPublico.OBRA, true,
                UUID.randomUUID(), "OBRAS", true, UUID.randomUUID(), Set.of(), null, List.of());
        estoqueObra.vincularObra(obra.id());
        SolicitacaoMaterial solicitacao = SolicitacaoMaterial.criarTecnica(
                obra, null, tipo, estoqueObra, almoxarifado,
                null, null, null, UUID.randomUUID());
        solicitacao.adicionarItemTecnico(material, quantidade);
        return solicitacao.getItens().getFirst();
    }

    private static <T extends BaseEntity> T comId(T entidade) {
        ReflectionTestUtils.setField(entidade, "id", UUID.randomUUID());
        return entidade;
    }
}
