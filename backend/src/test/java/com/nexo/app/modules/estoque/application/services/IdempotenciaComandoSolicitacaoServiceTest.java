package com.nexo.app.modules.estoque.application.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.estoque.infrastructure.persistence.ComandoIdempotenteSolicitacaoRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdempotenciaComandoSolicitacaoServiceTest {

    @Test
    void calculaMesmoHashParaMapasSemanticamenteIguaisEmOrdensDiferentes() {
        ComandoIdempotenteSolicitacaoRepository repository =
                mock(ComandoIdempotenteSolicitacaoRepository.class);
        UUID idUsuario = UUID.randomUUID();
        when(repository.findByEscopoAndIdUsuarioAndChave("entrega", idUsuario, "chave-a"))
                .thenReturn(Optional.empty());
        IdempotenciaComandoSolicitacaoService service = new IdempotenciaComandoSolicitacaoService(
                repository,
                new ObjectMapper(),
                new ObservabilidadeSolicitacaoTecnica(new SimpleMeterRegistry()));

        Map<String, Object> primeiraOrdem = new LinkedHashMap<>();
        primeiraOrdem.put("material", "MAT-1");
        primeiraOrdem.put("quantidade", 2);
        Map<String, Object> segundaOrdem = new LinkedHashMap<>();
        segundaOrdem.put("quantidade", 2);
        segundaOrdem.put("material", "MAT-1");

        var primeira = service.consultar(
                "entrega", idUsuario, "chave-a", primeiraOrdem, Void.class,
                ObservabilidadeSolicitacaoTecnica.Operacao.ENTREGA);
        var segunda = service.consultar(
                "entrega", idUsuario, "chave-a", segundaOrdem, Void.class,
                ObservabilidadeSolicitacaoTecnica.Operacao.ENTREGA);

        assertThat(segunda.hashPayload()).isEqualTo(primeira.hashPayload());
    }
}
