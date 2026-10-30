package com.nexo.app;

import com.nexo.app.modules.assistente.application.SemanticDatasetRegistry;
import com.nexo.app.modules.assistente.application.ConversaPersistencia;
import com.nexo.app.modules.assistente.application.dto.EnviarMensagemRequest;
import com.nexo.app.modules.assistente.application.navigation.NavigationActionCatalog;
import com.nexo.app.modules.assistente.application.navigation.NavigationActionProperties;
import com.nexo.app.modules.assistente.application.navigation.NavigationActionState;
import com.nexo.app.modules.assistente.application.navigation.NavigationParameterDestination;
import com.nexo.app.modules.assistente.domain.StatusMensagemAssistente;
import com.nexo.app.shared.application.analytics.SemanticQuery;
import com.nexo.app.shared.application.analytics.SemanticQueryContext;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CesApplicationTests {

    @Autowired
    private SemanticDatasetRegistry semanticDatasetRegistry;

    @Autowired
    private ConversaPersistencia conversaPersistencia;

    @Autowired
    private NavigationActionProperties navigationActionProperties;

    @Autowired
    private NavigationActionCatalog navigationActionCatalog;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Test
    void contextLoads() {
    }

    @Test
    void removesLegacyStatusApiFromApplicationMappings() {
        assertThat(requestMappingHandlerMapping.getHandlerMethods().keySet().toString())
                .doesNotContain("/v1/status");
    }

    @Test
    void navigationCatalogPublishesTheVersionedFrontendContract() {
        var entries = navigationActionProperties.entries();
        var active = entries.stream()
                .filter(entry -> entry.state() == NavigationActionState.ATIVO)
                .toList();
        var blocked = entries.stream()
                .filter(entry -> entry.state() == NavigationActionState.BLOQUEADO)
                .toList();
        var deploying = entries.stream()
                .filter(entry -> entry.state() == NavigationActionState.EM_IMPLANTACAO)
                .toList();
        var parameterized = entries.stream()
                .filter(entry -> !entry.parameters().isEmpty())
                .toList();

        assertThat(navigationActionProperties.version()).isEqualTo("1.1");
        assertThat(entries).hasSize(89);
        assertThat(entries.stream().map(NavigationActionProperties.Entry::key))
                .doesNotHaveDuplicates();
        assertThat(active).hasSize(85)
                .allSatisfy(entry -> assertThat(entry.requiredAuthorities()).isNotEmpty());
        assertThat(deploying).isEmpty();
        assertThat(blocked).extracting(NavigationActionProperties.Entry::key)
                .containsExactlyInAnyOrder(
                        "IAM_STATUS", "ALMOX_TRANSFERENCIA_NOVA",
                        "ALMOX_ENTRADA_AVULSA", "ALMOX_BAIXA_AVULSA");
        assertThat(parameterized).hasSize(18)
                .allSatisfy(entry -> assertThat(entry.parameters())
                        .anySatisfy(parameter -> {
                            assertThat(parameter.name()).isEqualTo("id");
                            assertThat(parameter.frontendDestination())
                                    .isEqualTo(NavigationParameterDestination.PATH);
                            assertThat(parameter.required()).isTrue();
                        }));
        assertThat(entries.stream()
                .filter(entry -> entry.parameters().stream().anyMatch(parameter -> parameter.name().equals("mode"))))
                .extracting(NavigationActionProperties.Entry::key)
                .containsExactlyInAnyOrder("DESCONTO_DETALHE", "EXECUCAO_COMERCIAL_DETALHE");
        assertThat(entry(entries, "DESCONTO_NOVO").requiredAuthorities())
                .containsExactly("desconto:solicitar");
        assertThat(entry(entries, "DESCONTOS_LISTA").requiredAuthorities())
                .containsExactly("desconto:read");
        assertThat(entry(entries, "EXECUCAO_COMERCIAL").requiredAuthorities())
                .containsExactly("execucao:comercial:read");

        Set<String> allAuthorities = active.stream()
                .flatMap(entry -> entry.requiredAuthorities().stream())
                .collect(Collectors.toSet());
        var authorized = navigationActionCatalog.listAuthorized(
                new SemanticQueryContext(UUID.randomUUID(), allAuthorities));
        assertThat(authorized).hasSize(85);
    }

    private NavigationActionProperties.Entry entry(
            List<NavigationActionProperties.Entry> entries,
            String key) {
        return entries.stream()
                .filter(entry -> entry.key().equals(key))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void semanticDatasetQueriesAreValidHql() {
        SemanticQueryContext context = new SemanticQueryContext(UUID.randomUUID(),
                Set.of("sst:dashboard:read", "desconto:read"));

        semanticDatasetRegistry.executar(aggregate("sst_inspecoes", "status_inspecao", "total_inspecoes"), context);
        semanticDatasetRegistry.executar(aggregate("sst_desvios", "gravidade", "total_desvios"), context);
        semanticDatasetRegistry.executar(aggregate("descontos", "status_desconto", "total_descontos"), context);
    }

    @Test
    void conversationOwnershipAndClientMessageIdArePersisted() {
        UUID owner = UUID.randomUUID();
        UUID clientMessageId = UUID.randomUUID();
        var conversa = conversaPersistencia.criar(owner, null, null);
        var request = new EnviarMensagemRequest(clientMessageId, "Pergunta de teste", List.of(), null);

        var primeira = conversaPersistencia.preparar(conversa.getId(), owner, request);
        assertThat(primeira.existente()).isNull();
        assertThat(primeira.primeiraPergunta()).isTrue();

        var repetidaDuranteProcessamento = conversaPersistencia.preparar(conversa.getId(), owner, request);
        assertThat(repetidaDuranteProcessamento.existente()).isNotNull();
        assertThat(repetidaDuranteProcessamento.existente().getStatus()).isEqualTo(StatusMensagemAssistente.PROCESSANDO);

        conversaPersistencia.concluir(primeira.mensagemAssistenteId(), StatusMensagemAssistente.CONCLUIDA,
                "Resposta de teste", "[]", "[]", null, null);
        var repetidaAposConclusao = conversaPersistencia.preparar(conversa.getId(), owner, request);
        assertThat(repetidaAposConclusao.existente().getConteudo()).isEqualTo("Resposta de teste");
        assertThat(repetidaAposConclusao.existente().getStatus()).isEqualTo(StatusMensagemAssistente.CONCLUIDA);

        assertThatThrownBy(() -> conversaPersistencia.preparar(conversa.getId(), UUID.randomUUID(), request))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    private SemanticQuery aggregate(String dataset, String dimension, String measure) {
        return new SemanticQuery(dataset, List.of(dimension), List.of(measure), List.of(),
                List.of(dimension), List.of(), 10);
    }

}
