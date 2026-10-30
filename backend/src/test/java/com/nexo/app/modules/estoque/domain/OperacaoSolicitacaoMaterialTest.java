package com.nexo.app.modules.estoque.domain;

import com.nexo.app.modules.obras.ObraApi.ObraPublica;
import com.nexo.app.modules.obras.ObraApi.TipoObraPublico;
import com.nexo.app.shared.domain.AuditIgnore;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.persistence.CryptoConverterImpl;
import jakarta.persistence.Convert;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperacaoSolicitacaoMaterialTest {

    @Test
    void assinaturasSaoCifradasEPuladasPelaAuditoriaGenerica() throws NoSuchFieldException {
        assertAssinaturaProtegida(OperacaoSolicitacaoMaterial.class, "assinaturaAlmox");
        assertAssinaturaProtegida(OperacaoSolicitacaoMaterial.class, "assinaturaEquipe");
        assertAssinaturaProtegida(SolicitacaoMaterial.class, "assinaturaAlmox");
        assertAssinaturaProtegida(SolicitacaoMaterial.class, "assinaturaEquipe");
    }

    @Test
    void entregaPreservaTipoEReferenciasDaSolicitacaoIndependente() {
        SolicitacaoMaterial solicitacao = solicitacaoTecnica(TipoSolicitacaoTecnica.ADICAO_COMUM);
        ReservaSolicitacaoTecnica reserva = reservar(solicitacao);
        UUID equipe = UUID.randomUUID();

        OperacaoSolicitacaoMaterial operacao = new OperacaoSolicitacaoMaterial(
                solicitacao, TipoOperacaoSolicitacao.ENTREGA, equipe, UUID.randomUUID(), null,
                "assinatura-almox", "assinatura-equipe", OrigemComandoTecnico.UI, null);
        ItemOperacaoSolicitacao item = operacao.adicionarItem(
                solicitacao.getItens().getFirst(), new BigDecimal("2.000"), null, null);

        assertThat(operacao.getSolicitacao()).isSameAs(solicitacao);
        assertThat(operacao.getTipoTecnico()).isEqualTo(TipoSolicitacaoTecnica.ADICAO_COMUM);
        assertThat(operacao.getTipoObraSnapshot()).isEqualTo(TipoObraSnapshot.OBRA);
        assertThat(operacao.getReserva()).isSameAs(reserva);
        assertThat(operacao.getIdEquipe()).isEqualTo(equipe);
        assertThat(item.getCodigoMaterialSnapshot()).isEqualTo("MAT-OPERACAO");
    }

    @Test
    void exigeReservaSomenteAoConfirmarMovimentacaoTecnica() {
        SolicitacaoMaterial solicitacao = solicitacaoTecnica(TipoSolicitacaoTecnica.ADICAO_COMUM);

        assertThatThrownBy(() -> new OperacaoSolicitacaoMaterial(
                solicitacao, TipoOperacaoSolicitacao.ENTREGA, null, UUID.randomUUID(), null,
                "assinatura-almox", "assinatura-equipe", OrigemComandoTecnico.UI, null))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("reserva valida");
    }

    @Test
    void estornoExigeOperacaoEItemOriginalComMotivo() {
        SolicitacaoMaterial solicitacao = solicitacaoTecnica(TipoSolicitacaoTecnica.ADICAO_COMUM);
        reservar(solicitacao);
        OperacaoSolicitacaoMaterial entrega = new OperacaoSolicitacaoMaterial(
                solicitacao, TipoOperacaoSolicitacao.ENTREGA, UUID.randomUUID(), UUID.randomUUID(), null,
                "assinatura-almox", "assinatura-equipe", OrigemComandoTecnico.UI, null);
        ItemOperacaoSolicitacao itemEntrega = entrega.adicionarItem(
                solicitacao.getItens().getFirst(), BigDecimal.ONE, null, null);

        assertThatThrownBy(() -> new OperacaoSolicitacaoMaterial(
                solicitacao, TipoOperacaoSolicitacao.ESTORNO, null, UUID.randomUUID(), null,
                null, null, OrigemComandoTecnico.UI, entrega))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("motivo");

        OperacaoSolicitacaoMaterial estorno = new OperacaoSolicitacaoMaterial(
                solicitacao, TipoOperacaoSolicitacao.ESTORNO, null, UUID.randomUUID(),
                "Quantidade indevida", null, null, OrigemComandoTecnico.UI, entrega);
        ItemOperacaoSolicitacao itemEstorno = estorno.adicionarItem(
                solicitacao.getItens().getFirst(), BigDecimal.ONE, null, itemEntrega);

        assertThat(estorno.getOperacaoOriginal()).isSameAs(entrega);
        assertThat(itemEstorno.getItemOperacaoOriginal()).isSameAs(itemEntrega);
    }

    private static ReservaSolicitacaoTecnica reservar(SolicitacaoMaterial solicitacao) {
        return new ReservaSolicitacaoTecnica(
                solicitacao, BigInteger.ONE, LocalDate.of(2026, 8, 1),
                null, null, null, UUID.randomUUID());
    }

    private static SolicitacaoMaterial solicitacaoTecnica(TipoSolicitacaoTecnica tipoTecnico) {
        Material material = comId(new Material(
                "MAT-OPERACAO", "Material para operacao", "UN", "MATR", null, null,
                false, UUID.randomUUID()));
        TipoEstoque tipoFisico = comId(new TipoEstoque("FISICO"));
        TipoEstoque tipoObra = comId(new TipoEstoque("OBRA"));
        Estoque almoxarifado = comId(new Estoque("ALMOXARIFADO", tipoFisico));
        Estoque estoqueObra = comId(new Estoque("ESTOQUE OBRA", tipoObra));
        ObraPublica obra = new ObraPublica(
                UUID.randomUUID(), "001", "Obra teste", TipoObraPublico.OBRA, true,
                UUID.randomUUID(), "OBRAS", true, UUID.randomUUID(), Set.of(), null, List.of());
        estoqueObra.vincularObra(obra.id());
        SolicitacaoMaterial solicitacao = SolicitacaoMaterial.criarTecnica(
                obra, null, tipoTecnico, estoqueObra, almoxarifado,
                null, null, null, UUID.randomUUID());
        solicitacao.adicionarItemTecnico(material, new BigDecimal("2.000"));
        return solicitacao;
    }

    private static <T extends BaseEntity> T comId(T entidade) {
        ReflectionTestUtils.setField(entidade, "id", UUID.randomUUID());
        return entidade;
    }

    private static void assertAssinaturaProtegida(Class<?> tipo, String nomeCampo)
            throws NoSuchFieldException {
        var campo = tipo.getDeclaredField(nomeCampo);
        assertThat(campo.isAnnotationPresent(AuditIgnore.class)).isTrue();
        assertThat(campo.getAnnotation(Convert.class).converter()).isEqualTo(CryptoConverterImpl.class);
    }
}
