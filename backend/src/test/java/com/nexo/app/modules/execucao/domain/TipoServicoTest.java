package com.nexo.app.modules.execucao.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TipoServicoTest {

    @Test
    @DisplayName("Código em maiúsculas, descrição sem espaços sobrando e normalizada sem acento")
    void normalizaDados() {
        TipoServico tipo = new TipoServico(" cl ", "  Ligação   nova ");

        assertThat(tipo.getCodigo()).isEqualTo("CL");
        assertThat(tipo.getDescricao()).isEqualTo("Ligação nova");
        assertThat(tipo.getDescricaoNormalizada()).isEqualTo("ligacao nova");
    }

    @Test
    @DisplayName("Código e descrição são obrigatórios e têm limite de tamanho")
    void validaDados() {
        assertThatThrownBy(() -> new TipoServico(" ", "Corte")).isInstanceOf(InvarianteVioladaException.class);
        assertThatThrownBy(() -> new TipoServico("CS", null)).isInstanceOf(InvarianteVioladaException.class);
        assertThatThrownBy(() -> new TipoServico("CS", "X")).isInstanceOf(InvarianteVioladaException.class);
        assertThatThrownBy(() -> new TipoServico("CODIGOLONGO", "Corte")).isInstanceOf(InvarianteVioladaException.class);
    }

    @Test
    @DisplayName("Tipo desativado não pode ser editado")
    void naoEditaDesativado() {
        TipoServico tipo = new TipoServico("CS", "Corte");
        tipo.desativar();

        assertThatThrownBy(() -> tipo.atualizar("CS", "Corte de energia"))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("Reative");
    }
}
