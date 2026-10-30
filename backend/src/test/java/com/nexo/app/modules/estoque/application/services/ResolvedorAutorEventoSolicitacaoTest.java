package com.nexo.app.modules.estoque.application.services;

import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResolvedorAutorEventoSolicitacaoTest {

    @Test
    void priorizaNomeDoColaboradorEUsaLoginParaUsuarioSemColaborador() {
        ColaboradorApi colaboradores = mock(ColaboradorApi.class);
        UsuarioApi usuarios = mock(UsuarioApi.class);
        ResolvedorAutorEventoSolicitacao resolvedor =
                new ResolvedorAutorEventoSolicitacao(colaboradores, usuarios);
        UUID idColaborador = UUID.randomUUID();
        UUID idSemColaborador = UUID.randomUUID();
        when(colaboradores.buscarNomeCompletoPorUsuario(idColaborador))
                .thenReturn(Optional.of("Thalisson"));
        when(colaboradores.buscarNomeCompletoPorUsuario(idSemColaborador))
                .thenReturn(Optional.empty());
        when(usuarios.buscarNomePorId(idSemColaborador)).thenReturn(Optional.of("USR001"));

        assertThat(resolvedor.resolver(idColaborador, "USUARIO " + idColaborador))
                .isEqualTo("Thalisson");
        assertThat(resolvedor.resolver(idSemColaborador, "USUARIO " + idSemColaborador))
                .isEqualTo("USR001");
    }
}
