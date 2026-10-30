package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RelacionamentoProjetoTest {

    @Test
    void deveCanonicalizarEPermitirNavegacaoNosDoisSentidos() {
        UUID menor = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID maior = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        Obra projetoMenor = obra(menor, "1000001");
        Obra projetoMaior = obra(maior, "1000002");

        RelacionamentoProjeto relacionamento =
                new RelacionamentoProjeto(projetoMaior, projetoMenor, TipoVinculoProjeto.MEDIDOR);

        assertThat(relacionamento.getProjetoA()).isSameAs(projetoMenor);
        assertThat(relacionamento.getProjetoB()).isSameAs(projetoMaior);
        assertThat(relacionamento.projetoRelacionadoA(menor)).isSameAs(projetoMaior);
        assertThat(relacionamento.projetoRelacionadoA(maior)).isSameAs(projetoMenor);
        assertThat(relacionamento.getTipo()).isEqualTo(TipoVinculoProjeto.MEDIDOR);
    }

    @Test
    void deveRejeitarAutoRelacionamentoEProjetosNaoPersistidos() {
        UUID id = UUID.randomUUID();
        Obra projeto = obra(id, "1000001");

        assertThatThrownBy(() -> new RelacionamentoProjeto(projeto, projeto, TipoVinculoProjeto.KIT))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("si mesmo");
        assertThatThrownBy(() -> new RelacionamentoProjeto(
                obra(null, "1000002"), projeto, TipoVinculoProjeto.KIT))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("persistidos");
    }

    private Obra obra(UUID id, String numero) {
        Obra obra = new Obra(
                numero, "PROJETO " + numero, null, null, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, null,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), new StatusObra("PLANEJADA", 1, false),
                null, null
        );
        if (id != null) {
            ReflectionTestUtils.setField(obra, "id", id);
        }
        return obra;
    }
}
