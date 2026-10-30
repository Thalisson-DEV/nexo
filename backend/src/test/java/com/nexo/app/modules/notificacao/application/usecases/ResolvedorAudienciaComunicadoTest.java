package com.nexo.app.modules.notificacao.application.usecases;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.notificacao.domain.ApresentacaoComunicado;
import com.nexo.app.modules.notificacao.domain.Comunicado;
import com.nexo.app.modules.notificacao.domain.PrioridadeNotificacao;
import com.nexo.app.modules.notificacao.domain.TipoAlvoSegmento;
import com.nexo.app.modules.notificacao.domain.TipoComunicado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResolvedorAudienciaComunicadoTest {

    @Mock
    private UsuarioApi usuarioApi;

    @InjectMocks
    private ResolvedorAudienciaComunicado resolvedor;

    private final UUID ana = UUID.randomUUID();
    private final UUID bruno = UUID.randomUUID();
    private final UUID carla = UUID.randomUUID();

    @Test
    void deveSomarOsSegmentosSemRepetirUsuario() {
        UUID idPerfil = UUID.randomUUID();
        Comunicado comunicado = novoComunicado();
        comunicado.adicionarSegmento(TipoAlvoSegmento.PERFIL, idPerfil, null);
        comunicado.adicionarSegmento(TipoAlvoSegmento.USUARIO, carla, null);

        when(usuarioApi.buscarIdsAtivosPorPerfil(idPerfil)).thenReturn(List.of(ana, bruno, carla));

        Set<UUID> destinatarios = resolvedor.resolver(comunicado);

        assertThat(destinatarios).containsExactlyInAnyOrder(ana, bruno, carla);
    }

    @Test
    void segmentoTodosDeveExpandirParaTodosOsUsuariosAtivos() {
        Comunicado comunicado = novoComunicado();
        comunicado.adicionarSegmento(TipoAlvoSegmento.TODOS, null, null);

        when(usuarioApi.buscarIdsAtivos()).thenReturn(List.of(ana, bruno));

        assertThat(resolvedor.resolver(comunicado)).containsExactlyInAnyOrder(ana, bruno);
        verify(usuarioApi, never()).buscarIdsAtivosPorPerfil(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void segmentoPorTipoDeveConsultarPeloValorNormalizado() {
        Comunicado comunicado = novoComunicado();
        comunicado.adicionarSegmento(TipoAlvoSegmento.TIPO_USUARIO, null, " campo ");

        when(usuarioApi.buscarIdsAtivosPorTipo("CAMPO")).thenReturn(List.of(bruno));

        assertThat(resolvedor.resolver(comunicado)).containsExactly(bruno);
    }

    @Test
    void comunicadoSemSegmentoNaoDeveAlcancarNinguem() {
        assertThat(resolvedor.resolver(novoComunicado())).isEmpty();
    }

    private Comunicado novoComunicado() {
        return new Comunicado("Aviso", null, "Conteúdo.", TipoComunicado.AVISO,
                ApresentacaoComunicado.CARD, null, null, null, false, false, false, false,
                PrioridadeNotificacao.NORMAL);
    }
}
