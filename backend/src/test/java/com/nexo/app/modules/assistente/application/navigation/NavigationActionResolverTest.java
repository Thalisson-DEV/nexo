package com.nexo.app.modules.assistente.application.navigation;

import com.nexo.app.shared.application.analytics.SemanticQueryContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.nexo.app.modules.assistente.application.navigation.NavigationActionProperties.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NavigationActionResolverTest {

    @Test
    void resolvesOnlyActiveAuthorizedKeyAndUsesServerMetadata() {
        var resolver = resolver(List.of(entry("DESCONTO_NOVO", NavigationActionState.ATIVO,
                List.of("desconto:solicitar"), List.of())));
        var context = context("desconto:solicitar");

        var result = resolver.resolve(new NavigationActionProposal(
                "NAVIGATE", "DESCONTO_NOVO", Map.of()), context);

        assertThat(result.accepted()).isTrue();
        assertThat(result.action()).hasValueSatisfying(action -> {
            assertThat(action.tipo()).isEqualTo("NAVIGATE");
            assertThat(action.chaveDestino()).isEqualTo("DESCONTO_NOVO");
            assertThat(action.rotulo()).isEqualTo("Rotulo controlado");
            assertThat(action.autoridade()).isEqualTo("desconto:solicitar");
        });
    }

    @Test
    void rejectsMissingAuthorityAndNonActiveEntriesWithoutLeakingDifference() {
        var resolver = resolver(List.of(
                entry("ATIVA", NavigationActionState.ATIVO, List.of("required"), List.of()),
                entry("IMPLANTANDO", NavigationActionState.EM_IMPLANTACAO, List.of(), List.of()),
                entry("BLOQUEADA", NavigationActionState.BLOQUEADO, List.of(), List.of())));

        assertThat(resolver.resolve(new NavigationActionProposal("NAVIGATE", "ATIVA", Map.of()), context()).rejection())
                .isEqualTo(NavigationActionResolver.Rejection.UNAVAILABLE_KEY);
        assertThat(resolver.resolve(new NavigationActionProposal("NAVIGATE", "IMPLANTANDO", Map.of()), context()).rejection())
                .isEqualTo(NavigationActionResolver.Rejection.UNAVAILABLE_KEY);
        assertThat(resolver.resolve(new NavigationActionProposal("NAVIGATE", "BLOQUEADA", Map.of()), context()).rejection())
                .isEqualTo(NavigationActionResolver.Rejection.UNAVAILABLE_KEY);
    }

    @Test
    void validatesRequiredExtraAndTypedParameters() {
        List<Parameter> parameters = List.of(
                parameter("id", NavigationParameterType.UUID, true, List.of()),
                parameter("mode", NavigationParameterType.ENUM, false, List.of("print")),
                parameter("page", NavigationParameterType.INTEGER, false, List.of()),
                parameter("amount", NavigationParameterType.DECIMAL, false, List.of()),
                parameter("date", NavigationParameterType.DATE, false, List.of()),
                parameter("search", NavigationParameterType.TEXT, false, List.of()));
        var resolver = resolver(List.of(entry("DETALHE", NavigationActionState.ATIVO, List.of("read"), parameters)));

        assertThat(resolver.resolve(new NavigationActionProposal("NAVIGATE", "DETALHE", Map.of()), context("read")).rejection())
                .isEqualTo(NavigationActionResolver.Rejection.MISSING_PARAMETERS);
        assertThat(resolver.resolve(new NavigationActionProposal(
                "NAVIGATE", "DETALHE", Map.of("id", UUID.randomUUID(), "extra", true)), context("read")).rejection())
                .isEqualTo(NavigationActionResolver.Rejection.INVALID_PARAMETERS);

        UUID id = UUID.randomUUID();
        var accepted = resolver.resolve(new NavigationActionProposal("NAVIGATE", "DETALHE", Map.of(
                "id", id,
                "mode", "PRINT",
                "page", "2",
                "amount", "10.25",
                "date", "2026-08-02",
                "search", "equipe alfa")), context("read"));

        assertThat(accepted.action()).hasValueSatisfying(action -> {
            assertThat(action.parametros().get("id")).isEqualTo(id.toString());
            assertThat(action.parametros().get("mode")).isEqualTo("print");
            assertThat(action.parametros().get("page")).isEqualTo(2L);
            assertThat(action.parametros().get("amount")).isEqualTo(new BigDecimal("10.25"));
            assertThat(action.parametros().get("date")).isEqualTo("2026-08-02");
        });
    }

    @Test
    void rejectsUnsafeTextAndInventedTypeOrKey() {
        var resolver = resolver(List.of(entry("BUSCA", NavigationActionState.ATIVO, List.of("read"),
                List.of(parameter("search", NavigationParameterType.TEXT, true, List.of())))));

        assertThat(resolver.resolve(new NavigationActionProposal(
                "WRITE", "BUSCA", Map.of("search", "ok")), context("read")).rejection())
                .isEqualTo(NavigationActionResolver.Rejection.INVALID_TYPE);
        assertThat(resolver.resolve(new NavigationActionProposal(
                "NAVIGATE", "INVENTADA", Map.of()), context("read")).rejection())
                .isEqualTo(NavigationActionResolver.Rejection.UNAVAILABLE_KEY);
        assertThat(resolver.resolve(new NavigationActionProposal(
                "NAVIGATE", "BUSCA", Map.of("search", "https://externo.invalid")), context("read")).rejection())
                .isEqualTo(NavigationActionResolver.Rejection.INVALID_PARAMETERS);
    }

    @Test
    void rejectsActiveEntryWithoutAuthorityAtStartup() {
        var properties = new NavigationActionProperties("1.1", List.of(
                entry("SEM_AUTORIDADE", NavigationActionState.ATIVO, List.of(), List.of())));

        assertThatThrownBy(() -> new NavigationActionCatalog(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SEM_AUTORIDADE");
    }

    private NavigationActionResolver resolver(List<Entry> entries) {
        return new NavigationActionResolver(new NavigationActionCatalog(new NavigationActionProperties("1.0", entries)));
    }

    private Entry entry(String key, NavigationActionState state, List<String> authorities, List<Parameter> parameters) {
        return new Entry(key, "Descricao para o modelo", "Rotulo controlado", authorities, null, parameters, state);
    }

    private Parameter parameter(String name, NavigationParameterType type, boolean required, List<String> values) {
        return new Parameter(name, type, NavigationParameterDestination.PATH, required, values, 200);
    }

    private SemanticQueryContext context(String... authorities) {
        return new SemanticQueryContext(UUID.randomUUID(), Set.of(authorities));
    }
}
