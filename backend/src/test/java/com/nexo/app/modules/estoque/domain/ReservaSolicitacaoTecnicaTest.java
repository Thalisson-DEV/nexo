package com.nexo.app.modules.estoque.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservaSolicitacaoTecnicaTest {

    @Test
    void preservaNumeroDeVinteDigitosSemPerdaDePrecisao() {
        BigInteger numero = new BigInteger("99999999999999999999");

        ReservaSolicitacaoTecnica reserva = novaReserva(novaSolicitacao(), numero, LocalDate.of(2026, 8, 1));

        assertThat(reserva.getNumero()).isEqualTo(numero);
        assertThat(reserva.getNumeroApi()).isEqualTo("99999999999999999999");
    }

    @Test
    void permiteMesmoNumeroEmFilhosDistintos() {
        BigInteger numero = new BigInteger("12345678901234567890");

        ReservaSolicitacaoTecnica primeira = novaReserva(novaSolicitacao(), numero, LocalDate.of(2026, 8, 1));
        ReservaSolicitacaoTecnica segunda = novaReserva(novaSolicitacao(), numero, LocalDate.of(2026, 8, 2));

        assertThat(primeira.getNumero()).isEqualTo(segunda.getNumero());
        assertThat(primeira.getSolicitacao()).isNotSameAs(segunda.getSolicitacao());
    }

    @Test
    void mantemDataRealSeparadaDoInstanteDeRegistroNoCes() {
        LocalDate dataReal = LocalDate.of(2026, 8, 1);
        OffsetDateTime antesDoRegistro = OffsetDateTime.now().minusSeconds(1);

        ReservaSolicitacaoTecnica reserva = novaReserva(novaSolicitacao(), BigInteger.TEN, dataReal);

        assertThat(reserva.getDataCriacao()).isEqualTo(dataReal);
        assertThat(reserva.getRegistradaEm()).isAfter(antesDoRegistro);
        assertThat(reserva.getRegistradaEm().toLocalDate()).isNotEqualTo(dataReal);
        assertThat(reserva.getIdUsuarioRegistro()).isNotNull();
    }

    @Test
    void impedeSegundaReservaNoMesmoFilho() {
        SolicitacaoMaterial solicitacao = novaSolicitacao();
        novaReserva(solicitacao, BigInteger.ONE, LocalDate.of(2026, 8, 1));

        assertThatThrownBy(() -> novaReserva(solicitacao, BigInteger.TWO, LocalDate.of(2026, 8, 2)))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("ja possui uma reserva");
    }

    @Test
    void permiteReservaSemAgenteResponsavel() {
        ReservaSolicitacaoTecnica reserva = new ReservaSolicitacaoTecnica(
                novaSolicitacao(), BigInteger.ONE, LocalDate.of(2026, 8, 1),
                null, null, null, UUID.randomUUID());

        assertThat(reserva.getIdAgenteResponsavel()).isNull();
        assertThat(reserva.getNomeAgenteSnapshot()).isNull();
    }

    @Test
    void rejeitaNumeroAcimaDaPrecisaoDoBanco() {
        assertThatThrownBy(() -> novaReserva(
                novaSolicitacao(),
                new BigInteger("100000000000000000000"),
                LocalDate.of(2026, 8, 1)
        )).isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("20 digitos");
    }

    private static ReservaSolicitacaoTecnica novaReserva(
            SolicitacaoMaterial solicitacao,
            BigInteger numero,
            LocalDate dataCriacao
    ) {
        return new ReservaSolicitacaoTecnica(
                solicitacao,
                numero,
                dataCriacao,
                UUID.randomUUID(),
                "Agente Responsavel",
                "Concessionaria",
                UUID.randomUUID()
        );
    }

    private static SolicitacaoMaterial novaSolicitacao() {
        TipoEstoque tipo = new TipoEstoque("FISICO");
        Estoque origem = comId(new Estoque("ALMOXARIFADO CENTRAL", tipo));
        Estoque destino = comId(new Estoque("ESTOQUE EQUIPE", tipo));
        Material material = new Material(
                "MAT-RESERVA",
                "Material para reserva",
                "UN",
                "MATR",
                null,
                null,
                false,
                UUID.randomUUID()
        );
        SolicitacaoMaterial solicitacao = new SolicitacaoMaterial(origem, destino, UUID.randomUUID());
        solicitacao.adicionarItem(material, BigDecimal.ONE);
        return solicitacao;
    }

    private static <T extends BaseEntity> T comId(T entidade) {
        ReflectionTestUtils.setField(entidade, "id", UUID.randomUUID());
        return entidade;
    }
}
