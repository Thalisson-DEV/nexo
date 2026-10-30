package com.nexo.app.shared.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedisConfigTest {

    private final CacheErrorHandler errorHandler = new RedisConfig().errorHandler();

    @Test
    void deveRemoverEntradaQuandoValorDoCacheNaoPodeSerDesserializado() {
        Cache cache = mock(Cache.class);
        when(cache.getName()).thenReturn(CacheNames.CHAMADO_TOPICOS);

        RuntimeException error = new RuntimeException(new SerializationException("JSON antigo"));

        errorHandler.handleCacheGetError(error, cache, "ativos");

        verify(cache).evict("ativos");
    }

    @Test
    void devePropagarErrosQueNaoSaoDeSerializacao() {
        Cache cache = mock(Cache.class);
        RuntimeException error = new IllegalStateException("Redis indisponivel");

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> errorHandler.handleCacheGetError(error, cache, "ativos"));

        assertSame(error, thrown);
        verifyNoInteractions(cache);
    }

    record Item(UUID id, String descricao, OffsetDateTime dataCriacao, List<String> marcadores) {
    }

    private final RedisSerializer<Object> serializador = RedisConfig.serializadorDeValores();

    private Object idaEVolta(Object valor) {
        return serializador.deserialize(serializador.serialize(valor));
    }

    private static Item item(String descricao) {
        return new Item(UUID.randomUUID(), descricao, OffsetDateTime.parse("2026-10-28T13:00:00Z"),
                Stream.of("a", "b").toList());
    }

    @Test
    void deveLerDeVoltaListaImutavelGravadaNoCache() {
        List<Item> itens = Stream.of(item("LIGAÇÃO NOVA"), item("CORTE")).toList();

        assertEquals(itens, idaEVolta(itens));
        assertEquals(List.of(), idaEVolta(List.of()));
        assertEquals(List.of(item("RELIGAÇÃO").descricao()), idaEVolta(List.of("RELIGAÇÃO")));
    }

    @Test
    void deveLerDeVoltaConjuntoEMapaImutaveisGravadosNoCache() {
        assertEquals(Set.of("CL", "CR"), idaEVolta(Set.of("CL", "CR")));
        assertEquals(Map.of("CL", 1, "CR", 2), idaEVolta(Map.of("CL", 1, "CR", 2)));
    }

    @Test
    void deveManterColecoesMutaveisERegistrosComoEstao() {
        Item unico = item("VISTORIA");
        List<Item> mutavel = new ArrayList<>(List.of(unico));

        assertEquals(unico, idaEVolta(unico));
        assertEquals(mutavel, idaEVolta(mutavel));
        assertEquals(new HashSet<>(Set.of("CL")), idaEVolta(new HashSet<>(Set.of("CL"))));
        assertEquals(new TreeMap<>(Map.of("CL", 1)), idaEVolta(new TreeMap<>(Map.of("CL", 1))));
    }
}
