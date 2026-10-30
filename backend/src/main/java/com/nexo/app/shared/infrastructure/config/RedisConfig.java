package com.nexo.app.shared.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.nexo.app.shared.infrastructure.config.CacheNames.*;

@Configuration
@EnableCaching
@Slf4j
public class RedisConfig implements CachingConfigurer {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        
        RedisSerializer<Object> serializer = serializadorDeValores();

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10)) 
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.string()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));

        RedisCacheConfiguration referenceDataConfig = defaultConfig.entryTtl(Duration.ofHours(48));

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        cacheConfigurations.put(BASES_OPERACIONAIS, referenceDataConfig);
        cacheConfigurations.put(CENTROS_FATURAMENTO, referenceDataConfig);
        cacheConfigurations.put(PROCESSOS_NEGOCIO, referenceDataConfig);
        cacheConfigurations.put(MUNICIPIOS, referenceDataConfig);
        cacheConfigurations.put(TIPOS_EQUIPE, referenceDataConfig);
        cacheConfigurations.put(FUNCOES, referenceDataConfig);
        cacheConfigurations.put(PERFIS_ACESSO, referenceDataConfig);
        cacheConfigurations.put(ACOES, referenceDataConfig);
        cacheConfigurations.put(ACOES_VALORES, referenceDataConfig);
        // Rota base -> obra muda só quando as coordenadas mudam (e a chave muda junto).
        cacheConfigurations.put(OBRAS_ROTAS, referenceDataConfig);
        cacheConfigurations.put(CBF, referenceDataConfig);
        cacheConfigurations.put(TIPOS_ESTOQUE, referenceDataConfig);
        cacheConfigurations.put(AVALIACOES_MATERIAL, referenceDataConfig);
        cacheConfigurations.put(CENTROS_INVESTIMENTO, referenceDataConfig);
        cacheConfigurations.put(MATERIAIS, referenceDataConfig);
        cacheConfigurations.put(TIPOS_SERVICO, referenceDataConfig);
        cacheConfigurations.put(CHAMADO_TOPICOS, referenceDataConfig);
        cacheConfigurations.put(CHAMADO_EQUIPES, referenceDataConfig);
        cacheConfigurations.put(LOOKUP_BOOTSTRAP, defaultConfig.entryTtl(Duration.ofMinutes(10)));

        cacheConfigurations.put(USUARIOS, defaultConfig.entryTtl(Duration.ofMinutes(60)));
        cacheConfigurations.put(COLABORADORES, defaultConfig.entryTtl(Duration.ofMinutes(60)));
        cacheConfigurations.put(EQUIPES, defaultConfig.entryTtl(Duration.ofMinutes(60)));
        cacheConfigurations.put(EQUIPES_MEMBROS, defaultConfig.entryTtl(Duration.ofMinutes(60)));
        cacheConfigurations.put(METAS_FATURAMENTO, defaultConfig.entryTtl(Duration.ofMinutes(60)));
        cacheConfigurations.put(VEICULOS, defaultConfig.entryTtl(Duration.ofMinutes(60)));
        cacheConfigurations.put(SALDOS, defaultConfig.entryTtl(Duration.ofMinutes(60)));
        cacheConfigurations.put(ESTOQUES, defaultConfig.entryTtl(Duration.ofMinutes(60)));
        cacheConfigurations.put(EXECUCOES, defaultConfig.entryTtl(Duration.ofMinutes(60)));

        // Metas de inspeção: configuração de gestão, alterada por ação de administrador
        // (sempre com evict) e lida a cada abertura da tela de metas.
        cacheConfigurations.put(METAS_INSPECAO, defaultConfig.entryTtl(Duration.ofMinutes(60)));

        // Vocabulários e atividades da governança de eventos: cadastro estável, sempre
        // alterado com evict, e lido inteiro a cada abertura do formulário de registro.
        cacheConfigurations.put(SST_CATALOGOS, referenceDataConfig);
        cacheConfigurations.put(SST_ATIVIDADES, referenceDataConfig);

        // Configuração do financeiro: muda por ação de administrador (sempre com evict),
        // mas é lida a cada abertura de formulário.
        cacheConfigurations.put(FINANCEIRO_FORMULARIOS, referenceDataConfig);
        cacheConfigurations.put(FINANCEIRO_FORNECEDORES, defaultConfig.entryTtl(Duration.ofMinutes(60)));

        // Preferências de notificação: alteradas apenas pelo próprio usuário (com evict),
        // mas consultadas a cada publicação dirigida a ele.
        cacheConfigurations.put(NOTIFICACAO_PREFERENCIAS, defaultConfig.entryTtl(Duration.ofHours(12)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    /**
     * Uma alteracao no contrato de um DTO pode deixar no Redis um JSON que o
     * serializador atual nao consegue mais ler. Nesse caso, trata apenas a
     * entrada como cache miss, remove a chave corrompida e permite que o metodo
     * de origem recalcule e grave o valor no formato vigente.
     */
    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new SimpleCacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                if (!containsSerializationException(exception)) {
                    super.handleCacheGetError(exception, cache, key);
                    return;
                }

                log.warn("Entrada incompativel no cache '{}' para a chave '{}'. "
                        + "A chave sera removida e o valor recalculado.", cache.getName(), key);

                try {
                    cache.evict(key);
                } catch (RuntimeException evictionException) {
                    log.warn("Nao foi possivel remover a chave '{}' do cache '{}'.",
                            key, cache.getName(), evictionException);
                }
            }
        };
    }

    /**
     * RedisSerializer.json() configura o Jackson para Records e tipagem polimórfica, mas grava
     * as coleções imutáveis do JDK ({@code stream().toList()}, {@code List.of}, {@code Set.of},
     * {@code Map.of}) sem a informação de tipo que ele mesmo exige na leitura. O valor entrava no
     * Redis e nunca saía: toda leitura falhava, a chave era removida e o método voltava ao banco.
     * Por isso as coleções seguem para o Redis como ArrayList, LinkedHashSet ou LinkedHashMap,
     * tipos que o serializador grava com o tipo e lê de volta.
     */
    static RedisSerializer<Object> serializadorDeValores() {
        RedisSerializer<Object> json = RedisSerializer.json();
        return new RedisSerializer<>() {
            @Override
            public byte[] serialize(Object value) {
                return json.serialize(comColecaoLegivel(value));
            }

            @Override
            public Object deserialize(byte[] bytes) {
                return json.deserialize(bytes);
            }
        };
    }

    private static Object comColecaoLegivel(Object value) {
        if (value instanceof List<?> lista && !(value instanceof ArrayList<?>)) {
            return new ArrayList<>(lista);
        }
        if (value instanceof Set<?> conjunto && !(value instanceof HashSet<?>) && !(value instanceof TreeSet<?>)) {
            return new LinkedHashSet<>(conjunto);
        }
        if (value instanceof Map<?, ?> mapa && !(value instanceof HashMap<?, ?>) && !(value instanceof TreeMap<?, ?>)) {
            return new LinkedHashMap<>(mapa);
        }
        return value;
    }

    private static boolean containsSerializationException(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SerializationException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
