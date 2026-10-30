package com.nexo.app.modules.notificacao.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nexo.app.modules.iam.ContextoAudienciaUsuario;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.notificacao.domain.GatilhoNotificacao;
import com.nexo.app.modules.notificacao.domain.OperadorCondicao;
import com.nexo.app.modules.notificacao.domain.PoliticaRoteamentoNotificacao;
import com.nexo.app.modules.notificacao.infrastructure.persistence.PoliticaRoteamentoNotificacaoRepository;

@ExtendWith(MockitoExtension.class)
class AvaliadorPoliticaRoteamentoTest {

    @Mock
    private PoliticaRoteamentoNotificacaoRepository repository;
    @Mock
    private UsuarioApi usuarioApi;
    @InjectMocks
    private AvaliadorPoliticaRoteamento avaliador;

    @Test
    void primeiraRotaSatisfeitaVenceEORestanteUsaFallback() {
        UUID politicaId = UUID.randomUUID();
        UUID primeiro = UUID.randomUUID();
        UUID segundo = UUID.randomUUID();
        UUID fallback = UUID.randomUUID();
        var politica = politica();
        var rotaPrimeira = politica.adicionarRota(10, false, primeiro);
        rotaPrimeira.adicionarCondicao("frota", OperadorCondicao.IGUAL, "true");
        rotaPrimeira.adicionarCondicao("base", OperadorCondicao.EM,
                "JUAZEIRO,REMANSO,JACOBINA,BONFIM");
        var rotaSegunda = politica.adicionarRota(20, false, segundo);
        rotaSegunda.adicionarCondicao("base", OperadorCondicao.EM, "JUAZEIRO,BONFIM");
        politica.adicionarRota(100, true, fallback);
        when(repository.findById(politicaId)).thenReturn(Optional.of(politica));
        ativo(primeiro);
        ativo(fallback);

        assertThat(avaliador.resolver(politicaId, gatilho(),
                Map.of("frota", true, "base", "BONFIM"))).contains(primeiro);
        assertThat(avaliador.resolver(politicaId, gatilho(),
                Map.of("frota", false, "base", "ARACAJU"))).contains(fallback);
    }

    @Test
    void politicaInativaOuUsuarioInativoNaoAmpliaDestinatarios() {
        UUID politicaId = UUID.randomUUID();
        UUID usuario = UUID.randomUUID();
        var politica = politica();
        var rota = politica.adicionarRota(10, false, UUID.randomUUID());
        rota.adicionarCondicao("frota", OperadorCondicao.IGUAL, "true");
        politica.adicionarRota(100, true, usuario);
        when(repository.findById(politicaId)).thenReturn(Optional.of(politica));
        when(usuarioApi.buscarContextoAudiencia(usuario)).thenReturn(Optional.empty());

        assertThat(avaliador.resolver(politicaId, gatilho(), Map.of("frota", false))).isEmpty();

        politica.desativar();
        assertThat(avaliador.resolver(politicaId, gatilho(), Map.of("frota", false))).isEmpty();
    }

    private void ativo(UUID id) {
        when(usuarioApi.buscarContextoAudiencia(id))
                .thenReturn(Optional.of(new ContextoAudienciaUsuario(id, UUID.randomUUID(), "ADMINISTRATIVO")));
    }

    private static PoliticaRoteamentoNotificacao politica() {
        return new PoliticaRoteamentoNotificacao(
                GatilhoNotificacao.FINANCEIRO_SOLICITACAO_SUBMETIDA, "Política", null);
    }

    private static String gatilho() {
        return GatilhoNotificacao.FINANCEIRO_SOLICITACAO_SUBMETIDA.getChave();
    }
}
