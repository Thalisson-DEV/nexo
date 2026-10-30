package com.nexo.app.modules.notificacao.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.notificacao.application.dto.PreferenciaNotificacaoResponse;
import com.nexo.app.modules.notificacao.domain.CategoriaNotificacao;
import com.nexo.app.modules.notificacao.domain.GatilhoNotificacao;
import com.nexo.app.modules.notificacao.domain.OperadorCondicao;
import com.nexo.app.modules.notificacao.domain.RegraNotificacao;
import com.nexo.app.modules.notificacao.domain.TipoDestinatarioRegra;
import com.nexo.app.modules.notificacao.infrastructure.persistence.RegraNotificacaoRepository;

class PoliticaObservabilidadeUseCasesTest {

    private final RegraNotificacaoRepository repository = mock(RegraNotificacaoRepository.class);
    private final ResolvedorDestinatariosRegra resolvedor = mock(ResolvedorDestinatariosRegra.class);
    private final PreferenciaNotificacaoUseCases preferencias = mock(PreferenciaNotificacaoUseCases.class);
    private final UsuarioApi usuarioApi = mock(UsuarioApi.class);
    private final PoliticaObservabilidadeUseCases useCases =
            new PoliticaObservabilidadeUseCases(repository, resolvedor, preferencias, usuarioApi);

    @Test
    void projetaAudienciaComPermissaoEmailEPreferenciaAtiva() {
        RegraNotificacao regra = regra(true);
        UUID elegivel = UUID.randomUUID();
        UUID semEmail = UUID.randomUUID();
        UUID silenciado = UUID.randomUUID();
        when(repository.findAllByChaveGatilhoOrderByIdAsc(
                PoliticaObservabilidadeUseCases.CHAVE_GATILHO)).thenReturn(List.of(regra));
        when(resolvedor.resolver(regra, Map.of(), Map.of()))
                .thenReturn(Set.of(elegivel, semEmail, silenciado));
        when(usuarioApi.buscarEmailsPorIds(Set.of(elegivel, semEmail, silenciado)))
                .thenReturn(Map.of(elegivel, "tecnico@ces.example", silenciado, "silenciado@ces.example"));
        when(preferencias.listar(elegivel)).thenReturn(preferencia(true));
        when(preferencias.listar(silenciado)).thenReturn(preferencia(false));

        var response = useCases.projetar();

        assertThat(response.schemaVersion()).isEqualTo("1");
        assertThat(response.validUntil()).isEqualTo(response.generatedAt().plusHours(24));
        assertThat(response.rules()).singleElement().satisfies(projected -> {
            assertThat(projected.emailEnabled()).isTrue();
            assertThat(projected.audience()).extracting("email")
                    .containsExactly("tecnico@ces.example");
        });
    }

    @Test
    void recusaCondicaoQueOAdapterNaoConsegueAvaliarOffline() {
        RegraNotificacao regra = regra(true);
        regra.adicionarCondicao("estadoInicial", OperadorCondicao.MAIOR_QUE, "1");
        when(repository.findAllByChaveGatilhoOrderByIdAsc(
                PoliticaObservabilidadeUseCases.CHAVE_GATILHO)).thenReturn(List.of(regra));

        assertThatThrownBy(useCases::projetar)
                .isInstanceOf(com.nexo.app.shared.domain.exception.InvarianteVioladaException.class)
                .hasMessageContaining("não é suportada pela avaliação offline");
    }

    @Test
    void mudaVersaoQuandoAudienciaMuda() {
        RegraNotificacao regra = regra(true);
        UUID tecnico = UUID.randomUUID();
        when(repository.findAllByChaveGatilhoOrderByIdAsc(
                PoliticaObservabilidadeUseCases.CHAVE_GATILHO)).thenReturn(List.of(regra));
        when(resolvedor.resolver(regra, Map.of(), Map.of())).thenReturn(Set.of(tecnico));
        when(preferencias.listar(tecnico)).thenReturn(preferencia(true));
        when(usuarioApi.buscarEmailsPorIds(Set.of(tecnico)))
                .thenReturn(Map.of(tecnico, "primeiro@ces.example"), Map.of(tecnico, "segundo@ces.example"));

        String primeira = useCases.projetar().version();
        String segunda = useCases.projetar().version();

        assertThat(primeira).isNotEqualTo(segunda);
    }

    @Test
    void preservaRegraDesativadaComAudienciaVazia() {
        RegraNotificacao regra = regra(true);
        regra.desativar();
        when(repository.findAllByChaveGatilhoOrderByIdAsc(
                PoliticaObservabilidadeUseCases.CHAVE_GATILHO)).thenReturn(List.of(regra));

        var response = useCases.projetar();

        assertThat(response.rules()).singleElement().satisfies(projected -> {
            assertThat(projected.enabled()).isFalse();
            assertThat(projected.emailEnabled()).isFalse();
            assertThat(projected.audience()).isEmpty();
        });
    }

    private static RegraNotificacao regra(boolean enviarEmail) {
        RegraNotificacao regra = new RegraNotificacao(
                GatilhoNotificacao.OBSERVABILITY_INCIDENTE_ABERTO,
                "Regra de incidente", null, CategoriaNotificacao.SISTEMA, null,
                "Incidente em {{componente}}",
                "[{{ambiente}}] {{componente}} está {{estadoInicial}}. {{resumo}}",
                "{{linkStatus}}", null, 0, enviarEmail, false);
        regra.adicionarDestinatario(TipoDestinatarioRegra.PERMISSAO, null, "chamado:manage");
        return regra;
    }

    private static List<PreferenciaNotificacaoResponse> preferencia(boolean email) {
        return List.of(new PreferenciaNotificacaoResponse(
                CategoriaNotificacao.SISTEMA, "Sistema", true, email, true));
    }
}
