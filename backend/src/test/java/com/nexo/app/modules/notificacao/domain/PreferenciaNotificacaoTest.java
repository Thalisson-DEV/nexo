package com.nexo.app.modules.notificacao.domain;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreferenciaNotificacaoTest {

    @Test
    void devePermitirApenasOsCanaisLigados() {
        PreferenciaNotificacao preferencia = new PreferenciaNotificacao(
                UUID.randomUUID(), CategoriaNotificacao.OPERACIONAL, true, false);

        assertThat(preferencia.permite(CanalNotificacao.IN_APP)).isTrue();
        assertThat(preferencia.permite(CanalNotificacao.EMAIL)).isFalse();
    }

    @Test
    void ajustarDeveTrocarOsDoisCanais() {
        PreferenciaNotificacao preferencia = new PreferenciaNotificacao(
                UUID.randomUUID(), CategoriaNotificacao.SEGURANCA, true, false);

        preferencia.ajustar(false, true);

        assertThat(preferencia.isInApp()).isFalse();
        assertThat(preferencia.isEmail()).isTrue();
    }

    @Test
    void deveRecusarPreferenciaSemUsuarioOuCategoria() {
        assertThatThrownBy(() -> new PreferenciaNotificacao(null, CategoriaNotificacao.SISTEMA, true, false))
                .isInstanceOf(InvarianteVioladaException.class);

        assertThatThrownBy(() -> new PreferenciaNotificacao(UUID.randomUUID(), null, true, false))
                .isInstanceOf(InvarianteVioladaException.class);
    }
}
