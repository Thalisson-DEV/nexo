package com.nexo.app.modules.notificacao.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.notificacao.domain.GatilhoNotificacao;
import com.nexo.app.modules.notificacao.domain.RegraNotificacao;
import com.nexo.app.modules.notificacao.domain.TipoDestinatarioRegra;

class ResolvedorDestinatariosPoliticaTest {

    @Test
    void duasRegrasReutilizamAMesmaPolitica() {
        UsuarioApi usuarioApi = org.mockito.Mockito.mock(UsuarioApi.class);
        AvaliadorPoliticaRoteamento avaliador =
                org.mockito.Mockito.mock(AvaliadorPoliticaRoteamento.class);
        UUID politicaId = UUID.randomUUID();
        UUID diretor = UUID.randomUUID();
        Map<String, Object> contexto = Map.of("frota", true, "base", "BONFIM");
        when(avaliador.resolver(politicaId,
                GatilhoNotificacao.FINANCEIRO_SOLICITACAO_SUBMETIDA.getChave(), contexto))
                .thenReturn(java.util.Optional.of(diretor));
        var resolvedor = new ResolvedorDestinatariosRegra(usuarioApi, avaliador);
        RegraNotificacao compra = regra("Compra");
        RegraNotificacao pagamento = regra("Pagamento");
        compra.adicionarDestinatario(TipoDestinatarioRegra.POLITICA, politicaId, null);
        pagamento.adicionarDestinatario(TipoDestinatarioRegra.POLITICA, politicaId, null);

        assertThat(resolvedor.resolver(compra, contexto, Map.of())).isEqualTo(Set.of(diretor));
        assertThat(resolvedor.resolver(pagamento, contexto, Map.of())).isEqualTo(Set.of(diretor));
        verify(avaliador, times(2)).resolver(politicaId,
                GatilhoNotificacao.FINANCEIRO_SOLICITACAO_SUBMETIDA.getChave(), contexto);
    }

    private static RegraNotificacao regra(String nome) {
        return new RegraNotificacao(
                GatilhoNotificacao.FINANCEIRO_SOLICITACAO_SUBMETIDA,
                nome, null, null, null,
                "Aguardando", "Solicitação {{numero}}", null,
                null, 0, true, false);
    }
}
